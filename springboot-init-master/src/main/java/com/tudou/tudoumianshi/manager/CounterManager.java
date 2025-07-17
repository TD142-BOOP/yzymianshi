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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

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

    // 跟踪需要批量刷新的 key
    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();

    public static final String LUA_SCRIPT =
            "local exists = redis.call('exists', KEYS[1])\n" +
                    "if exists == 1 then\n" +
                    "    redis.call('set', KEYS[1], ARGV[1])\n" +
                    "else\n" +
                    "    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])\n" +
                    "end\n" +
                    "return ARGV[1]";

    // 本地缓存
    private final Cache<String, Integer> counterCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
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
        // 标记为脏数据，待定时刷新
        dirtyKeys.add(redisKey);
        // 优先读取本地缓存
        Integer newValue = counterCache.getIfPresent(redisKey);
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

    // 新增：每5秒批量同步计数到 Redis
    @Scheduled(fixedRate = 5000)
    public void flushCounterEvents() {
        if (dirtyKeys.isEmpty()) {
            return;
        }
        // 批量发送脏数据，并移除标记
        Set<String> keysToFlush = new HashSet<>(dirtyKeys);
        for (String k : keysToFlush) {
            Integer v = counterCache.getIfPresent(k);
            if (v != null) {
                counterProducer.sendAsync(new CounterEvent(k, v))
                    .exceptionally(ex -> {
                        log.error("定时发送计数事件失败 key={} value={}", k, v, ex);
                        return null;
                    });
            }
            dirtyKeys.remove(k);
        }
    }
}
