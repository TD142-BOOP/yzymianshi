package com.tudou.tudoumianshi.manager;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 本地计数管理器——使用 Pulsar 事件驱动将过期的计数同步到 Redis
 */
@Slf4j
@Service
public class CounterManager {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private PulsarClient   pulsarClient;

    private Producer<CounterEvent> counterProducer;

    public static final String LUA_SCRIPT =
            "local exists = redis.call('exists', KEYS[1])\n" +
                    "if exists == 1 then\n" +
                    "    redis.call('set', KEYS[1], ARGV[1])\n" +
                    "else\n" +
                    "    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])\n" +
                    "end\n" +
                    "return ARGV[1]";

    // 本地缓存，过期时通过 Pulsar 事件发送同步命令
    private final Cache<String, Integer> counterCache = Caffeine.newBuilder()
            .expireAfterWrite(80, TimeUnit.SECONDS)
            .removalListener((key, value, cause) -> {
                if (cause.wasEvicted() || cause == RemovalCause.EXPLICIT) {
                    counterProducer.sendAsync(new CounterEvent((String) key, (Integer) value))
                            .exceptionally(ex -> {
                                log.error("发送计数事件失败 key={} value={}", key, value, ex);
                                return null;
                            });
                }
            })
            .build();

    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }

    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        int expireSec;
        switch (timeUnit) {
            case SECONDS: expireSec = timeInterval; break;
            case MINUTES: expireSec = timeInterval * 60; break;
            case HOURS:   expireSec = timeInterval * 3600; break;
            default: throw new IllegalArgumentException("Unsupported TimeUnit");
        }
        return incrAndGetCounter(key, timeInterval, timeUnit, expireSec);
    }

    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit, int expirationSec) {
        if (StrUtil.isBlank(key)) return 0;
        String redisKey = generateRedisKey(key, timeInterval, timeUnit);
        // 更新本地缓存中的计数
        counterCache.asMap().compute(redisKey, (k, v) -> v == null ? 1 : v + 1);
        // 写入缓存后立即发送 Pulsar 同步事件
        Integer newValue = counterCache.getIfPresent(redisKey);
        counterProducer.sendAsync(new CounterEvent(redisKey, newValue))
            .exceptionally(ex -> { log.error("发送计数事件失败 key={} value={}", redisKey, newValue, ex); return null; });
        // 优先读取本地缓存
        if (newValue != null) {
            return newValue.longValue();
        }
        // 如果本地缓存中没有，再回退到 Redis
        long redisCount = redissonClient.getAtomicLong(redisKey).get();
        counterCache.put(redisKey, (int) redisCount);
        return redisCount;
    }

    private String generateRedisKey(String key, int interval, TimeUnit tu) {
        long factor;
        switch (tu) {
            case SECONDS: factor = Instant.now().getEpochSecond() / interval; break;
            case MINUTES: factor = Instant.now().getEpochSecond() / 60 / interval; break;
            case HOURS:   factor = Instant.now().getEpochSecond() / 3600 / interval; break;
            default: throw new IllegalArgumentException("Unsupported TimeUnit");
        }
        return key + ":" + factor;
    }

    @PostConstruct
    public void init() throws PulsarClientException {
        // 创建 Pulsar 生产者，用于发送 CounterEvent
        counterProducer = pulsarClient
                .newProducer(Schema.JSON(CounterEvent.class))
                .topic("counter-sync-topic")
                .create();
    }

    @PreDestroy
    public void destroy() {
        if (counterProducer != null) {
            try {
                counterProducer.close();
            } catch (PulsarClientException e) {
                log.error("关闭 CounterEvent 生产者失败", e);
            }
        }
    }
}
