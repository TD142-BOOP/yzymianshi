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
            while (true) {
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

        // 按(userId, questionId)分组，并获取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = new HashMap<>();

        for (ThumbEvent event : events) {
            Pair<Long, Long> key = Pair.of(event.getUserId(), event.getQuestionId());
            if (!latestEvents.containsKey(key) ||
                event.getEventTime().isAfter(latestEvents.get(key).getEventTime())) {
                latestEvents.put(key, event);
            }
        }

        latestEvents.forEach((userQuestionPair, event) -> {
            if (event == null) {
                return;
            }
            ThumbEvent.EventType finalAction = event.getType();

            if (finalAction == ThumbEvent.EventType.INCR) {
                countMap.merge(event.getQuestionId(), 1L, Long::sum);
                Thumb thumb = new Thumb();
                thumb.setQuestionId(event.getQuestionId());
                thumb.setUserId(event.getUserId());
                thumbs.add(thumb);
            } else {
                needRemove.set(true);
                wrapper.or().eq(Thumb::getUserId, event.getUserId()).eq(Thumb::getQuestionId, event.getQuestionId());
                countMap.merge(event.getQuestionId(), -1L, Long::sum);
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
        if (!thumbs.isEmpty()) {
            // 分批次插入
            thumbService.saveBatch(thumbs, 500);
        }
    }
}
