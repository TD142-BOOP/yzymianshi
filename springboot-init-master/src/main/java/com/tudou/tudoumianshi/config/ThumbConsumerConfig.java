package com.tudou.tudoumianshi.config;

import org.apache.pulsar.client.api.*;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Pulsar消费者配置
 * JDK 8兼容版本
 */
@Configuration
public class ThumbConsumerConfig {

    @Autowired(required = false)
    private PulsarClient pulsarClient;

    /**
     * 创建批量接收策略
     */
    @Bean
    public BatchReceivePolicy batchReceivePolicy() {
        // 显式使用Builder模式，避免类型推断问题
        return BatchReceivePolicy.builder()
                .maxNumMessages(1000)      // 每次处理1000条
                .timeout(10000, TimeUnit.MILLISECONDS) // 超时时间10秒
                .build();
    }

    /**
     * 自定义消费者Builder
     */
    @Bean
    public ConsumerBuilder<byte[]> customConsumerBuilder() {
        if (pulsarClient != null) {
            // 创建消费者Builder并应用批量接收策略
            return pulsarClient.newConsumer()
                    .batchReceivePolicy(batchReceivePolicy());
        }
        return null;
    }

    // 配置 NACK 重试策略
    @Bean
    public RedeliveryBackoff negativeAckRedeliveryBackoff() {
        return MultiplierRedeliveryBackoff.builder()
                // 初始延迟 1 秒
                .minDelayMs(1000)
                // 最大延迟 60 秒
                .maxDelayMs(60_000)
                // 每次重试延迟倍数
                .multiplier(2)
                .build();
    }

    // 配置 ACK 超时重试策略
    @Bean
    public RedeliveryBackoff ackTimeoutRedeliveryBackoff() {
        return MultiplierRedeliveryBackoff.builder()
                // 初始延迟 5 秒
                .minDelayMs(5000)
                // 最大延迟 300 秒
                .maxDelayMs(300_000)
                .multiplier(3)
                .build();
    }
    @Bean
    public DeadLetterPolicy deadLetterPolicy() {
        return DeadLetterPolicy.builder()
                // 最大重试次数
                .maxRedeliverCount(3)
                // 死信主题名称
                .deadLetterTopic("thumb-dlq-topic")
                .build();
    }

}
