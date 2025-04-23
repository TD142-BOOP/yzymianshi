package com.tudou.tudoumianshi.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tudou.tudoumianshi.listener.thumb.ThumbEvent;
import com.tudou.tudoumianshi.mapper.QuestionMapper;
import com.tudou.tudoumianshi.model.entity.Thumb;
import com.tudou.tudoumianshi.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pulsar.client.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.PulsarClientException.AlreadyClosedException;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {

    private final QuestionMapper questionMapper;
    private final ThumbService thumbService;
    private final PulsarClient pulsarClient;
    @Resource
    private BatchReceivePolicy batchReceivePolicy;
    @Resource
    private RedeliveryBackoff negativeAckRedeliveryBackoff;
    @Resource
    private RedeliveryBackoff ackTimeoutRedeliveryBackoff;
    @Resource
    private DeadLetterPolicy deadLetterPolicy;

    private Consumer<byte[]> consumer;
    // 控制接收线程的运行状态
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        try {
            consumer = pulsarClient.newConsumer()
                    .topic("thumb-topic")
                    .subscriptionName("thumb-subscription")
                    .subscriptionType(SubscriptionType.Shared)
                    .batchReceivePolicy(batchReceivePolicy)
                    .negativeAckRedeliveryBackoff(negativeAckRedeliveryBackoff)
                    .ackTimeoutRedeliveryBackoff(ackTimeoutRedeliveryBackoff)
                    .deadLetterPolicy(deadLetterPolicy)
                    .subscribe();

            // 启动一个线程来处理消息
            new Thread(this::receiveMessages).start();
        } catch (PulsarClientException e) {
            log.error("初始化Pulsar消费者失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        // 停止消息接收循环
        running = false;
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (PulsarClientException e) {
            log.error("关闭Pulsar消费者失败", e);
        }
    }

    private void receiveMessages() {
        try {
            while (running) {
                try {
                    // 批量接收消息
                    Messages<byte[]> messages = consumer.batchReceive();
                    if (messages != null) {
                        List<Message<byte[]>> messageList = new ArrayList<>();
                        for (Message<byte[]> msg : messages) {
                            messageList.add(msg);
                        }

                        if (!messageList.isEmpty()) {
                            processBatch(messageList);
                            // 确认消息
                            for (Message<byte[]> msg : messageList) {
                                consumer.acknowledge(msg);
                            }
                        }
                    }
                } catch (AlreadyClosedException e) {
                    log.info("Pulsar Consumer 已关闭，退出接收循环");
                    break;
                } catch (Exception e) {
                    log.error("处理消息失败", e);
                }

                // 防止CPU占用过高
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("消息接收线程异常退出", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<Message<byte[]>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size());
        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        List<Thumb> thumbs = new ArrayList<>();

        // 并行处理消息
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false);

        // 提取事件并过滤无效消息
        List<ThumbEvent> events = new ArrayList<>();

        for (Message<byte[]> message : messages) {
            try {
                // 将消息转换为ThumbEvent对象
                String jsonStr = new String(message.getData());
                ThumbEvent event = parseJsonToThumbEvent(jsonStr);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.error("解析消息失败", e);
            }
        }

        // 分组处理每个 (userId, questionId)，支持切换场景：先删除再插入
        Map<Pair<Long, Long>, List<ThumbEvent>> grouped = new HashMap<>();
        for (ThumbEvent evt : events) {
            Pair<Long, Long> key = Pair.of(evt.getUserId(), evt.getQuestionId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(evt);
        }
        grouped.forEach((userQuestionPair, evts) -> {
            // 按时间排序
            evts.sort(Comparator.comparing(ThumbEvent::getEventTime));
            ThumbEvent last = evts.get(evts.size() - 1);
            boolean hasDecr = evts.stream().anyMatch(e -> e.getType() == ThumbEvent.EventType.DECR);
            if (hasDecr) {
                needRemove.set(true);
                wrapper.or()
                    .eq(Thumb::getUserId, userQuestionPair.getLeft())
                    .eq(Thumb::getQuestionId, userQuestionPair.getRight());
            }
            if (last.getType() == ThumbEvent.EventType.INCR) {
                countMap.merge(userQuestionPair.getRight(), 1L, Long::sum);
                Thumb thumb = new Thumb();
                thumb.setQuestionId(userQuestionPair.getRight());
                thumb.setUserId(userQuestionPair.getLeft());
                thumbs.add(thumb);
            } else {
                // 最终为取消操作
                countMap.merge(userQuestionPair.getRight(), -1L, Long::sum);
            }
        });

        // 批量更新数据库
        if (needRemove.get()) {
            thumbService.remove(wrapper);
        }
        batchUpdateQuestions(countMap);
        batchInsertThumbs(thumbs);
    }

    private ThumbEvent parseJsonToThumbEvent(String json) {
        try {
            // 简单的JSON解析实现
            if (json == null || json.isEmpty()) {
                return null;
            }

            ThumbEvent event = new ThumbEvent();

            // 解析questionId
            int questionIdStart = json.indexOf("\"questionId\":") + 13;
            int questionIdEnd = json.indexOf(",", questionIdStart);
            String questionIdStr = json.substring(questionIdStart, questionIdEnd);
            event.setQuestionId(Long.parseLong(questionIdStr));

            // 解析userId
            int userIdStart = json.indexOf("\"userId\":") + 9;
            int userIdEnd = json.indexOf(",", userIdStart);
            String userIdStr = json.substring(userIdStart, userIdEnd);
            event.setUserId(Long.parseLong(userIdStr));

            // 解析type
            int typeStart = json.indexOf("\"type\":\"") + 8;
            int typeEnd = json.indexOf("\"", typeStart);
            String typeStr = json.substring(typeStart, typeEnd);
            event.setType(ThumbEvent.EventType.valueOf(typeStr));

            // 解析eventTime
            int timeStart = json.indexOf("\"eventTime\":\"") + 13;
            int timeEnd = json.indexOf("\"", timeStart);
            String timeStr = json.substring(timeStart, timeEnd);
            event.setEventTime(LocalDateTime.parse(timeStr));

            return event;
        } catch (Exception e) {
            log.error("解析ThumbEvent失败: {}", json, e);
            return null;
        }
    }

    public void batchUpdateQuestions(Map<Long, Long> countMap) {
        if (!countMap.isEmpty()) {
            questionMapper.batchUpdateThumbCount(countMap);
        }
    }

    public void batchInsertThumbs(List<Thumb> thumbs) {
        if (thumbs.isEmpty()) {
            return;
        }
        // 过滤掉数据库中已存在的(userId,questionId)组合，避免主键冲突
        List<Thumb> toInsert = thumbs.stream()
            .filter(t -> thumbService.count(
                new LambdaQueryWrapper<Thumb>()
                    .eq(Thumb::getUserId, t.getUserId())
                    .eq(Thumb::getQuestionId, t.getQuestionId())
            ) == 0)
            .collect(Collectors.toList());
        if (!toInsert.isEmpty()) {
            // 分批次插入
            thumbService.saveBatch(toInsert, 500);
        }
    }
}
