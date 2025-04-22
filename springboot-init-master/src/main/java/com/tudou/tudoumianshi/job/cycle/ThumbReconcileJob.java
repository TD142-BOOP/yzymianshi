package com.tudou.tudoumianshi.job.cycle;

import com.alibaba.nacos.shaded.com.google.common.collect.Sets;
import com.tudou.tudoumianshi.constant.ThumbConstant;
import com.tudou.tudoumianshi.listener.thumb.ThumbEvent;
import com.tudou.tudoumianshi.model.entity.Thumb;
import com.tudou.tudoumianshi.service.ThumbService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ThumbReconcileJob {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private PulsarClient pulsarClient;

    /**
     * 定时任务入口（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        long startTime = System.currentTimeMillis();

        // 1. 获取该分片下的所有用户ID
        Set<Long> userIds = new HashSet<>();
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                userIds.add(userId);
            }
        }

        // 2. 逐用户比对
        userIds.forEach(userId -> {
            Set<Long> redisQuestionIds = redisTemplate.opsForHash().keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId).stream().map(obj -> Long.valueOf(obj.toString())).collect(Collectors.toSet());
            Set<Long> mysqlQuestionIds = Optional.ofNullable(thumbService.lambdaQuery()
                            .eq(Thumb::getUserId, userId)
                            .list()
                    ).orElse(new ArrayList<>())
                    .stream()
                    .map(Thumb::getQuestionId)
                    .collect(Collectors.toSet());

            // 3. 计算差异（Redis有但MySQL无）
            Set<Long> diffQuestionIds = Sets.difference(redisQuestionIds, mysqlQuestionIds);

            // 4. 发送补偿事件
            sendCompensationEvents(userId, diffQuestionIds);
        });

        log.info("对账任务完成，耗时 {}ms", System.currentTimeMillis() - startTime);
    }

    /**
     * 发送补偿事件到Pulsar
     */
    private void sendCompensationEvents(Long userId, Set<Long> questionIds) {
        try (Producer<ThumbEvent> producer = pulsarClient.newProducer(Schema.JSON(ThumbEvent.class))
                .topic("thumb-topic")
                .create()) {
            questionIds.forEach(questionId -> {
                ThumbEvent thumbEvent = new ThumbEvent(userId, questionId, ThumbEvent.EventType.INCR, LocalDateTime.now());
                producer.sendAsync(thumbEvent)
                        .exceptionally(ex -> {
                            log.error("补偿事件发送失败: userId={}, questionId={}", userId, questionId, ex);
                            return null;
                        });
            });
        } catch (PulsarClientException e) {
            log.error("初始化Pulsar生产者失败", e);
        }
    }
}
