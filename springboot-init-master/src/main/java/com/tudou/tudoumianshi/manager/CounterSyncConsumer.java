package com.tudou.tudoumianshi.manager;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;
import org.apache.pulsar.client.api.PulsarClientException.AlreadyClosedException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CounterSyncConsumer {

    private final PulsarClient pulsarClient;
    private final RedissonClient redissonClient;
    private Consumer<CounterEvent> consumer;
    private volatile boolean running = true;

    public CounterSyncConsumer(PulsarClient pulsarClient, RedissonClient redissonClient) {
        this.pulsarClient = pulsarClient;
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        try {
            consumer = pulsarClient.newConsumer(Schema.JSON(CounterEvent.class))
                    .topic("counter-sync-topic")
                    .subscriptionName("counter-sync-subscription")
                    .subscriptionType(SubscriptionType.Shared)
                    .batchReceivePolicy(BatchReceivePolicy.builder()
                            .maxNumMessages(500)
                            .timeout(5000, TimeUnit.MILLISECONDS)
                            .build())
                    .subscribe();
            new Thread(this::receiveMessages).start();
        } catch (PulsarClientException e) {
            log.error("初始化 CounterSyncConsumer 失败", e);
        }
    }

    private void receiveMessages() {
        while (running) {
            try {
                Messages<CounterEvent> messages = consumer.batchReceive();
                if (messages != null) {
                    for (Message<CounterEvent> msg : messages) {
                        try {
                            CounterEvent evt = msg.getValue();
                            syncToRedis(evt.getKey(), evt.getValue());
                            consumer.acknowledge(msg);
                            log.info("处理计数同步消息成功");
                        } catch (Exception ex) {
                            log.error("处理计数同步消息失败", ex);
                            consumer.negativeAcknowledge(msg);
                        }
                    }
                }
            } catch (AlreadyClosedException e) {
                log.info("Pulsar Consumer 已关闭，退出接收循环");
                break;
            } catch (Exception e) {
                log.error("接收 CounterEvent 消息失败", e);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void syncToRedis(String key, Integer value) {
        try {
            RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
            script.eval(RScript.Mode.READ_WRITE,
                    CounterManager.LUA_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(key),
                    value,
                    60);
            log.info("Synced key: {}, value: {} to Redis", key, value);
        } catch (Exception e) {
            log.error("执行 Redis 脚本失败 key={} value={}", key, value, e);
            throw e;
        }
    }

    @PreDestroy
    public void close() {
        running = false;
        if (consumer != null) {
            try {
                consumer.close();
            } catch (PulsarClientException e) {
                log.error("关闭 CounterSyncConsumer 失败", e);
            }
        }
    }
}
