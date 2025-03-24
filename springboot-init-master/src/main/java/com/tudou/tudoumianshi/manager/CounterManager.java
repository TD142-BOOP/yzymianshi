package com.tudou.tudoumianshi.manager;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class CounterManager {

    @Resource
    private RedissonClient redissonClient;

    private static final String LUA_SCRIPT =
            "local exists = redis.call('exists', KEYS[1])\n" +
                    "if exists == 1 then\n" +
                    "    redis.call('set', KEYS[1], ARGV[1])\n" +
                    "else\n" +
                    "    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])\n" +
                    "end\n" +
                    "return ARGV[1]";

    // 自定义线程池配置
    private static final ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
            10,             // 核心线程数
            20,             // 最大线程数
            60L,            // 线程空闲存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(5000), // 调整队列容量
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理任务
    );

    // 全局线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 信号量，限制并发任务数
    private final Semaphore semaphore = new Semaphore(20); // 限制并发任务数为 20

    // 本地缓存
    private final Cache<String, Integer> counterCache = Caffeine.newBuilder()
            .expireAfterWrite(80, TimeUnit.SECONDS) // 本地缓存过期时间
            .removalListener((key, value, cause) -> {
                if (cause.wasEvicted() || cause == RemovalCause.EXPLICIT) {
                    syncToRedis((String) key, (Integer) value);
                }
            })
            .build();

    /**
     * 增加并返回计数，默认统计一分钟内的计数结果
     */
    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }

    /**
     * 增加并返回计数
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        int expirationTimeInSeconds;
        switch (timeUnit) {
            case SECONDS:
                expirationTimeInSeconds = timeInterval;
                break;
            case MINUTES:
                expirationTimeInSeconds = timeInterval * 60;
                break;
            case HOURS:
                expirationTimeInSeconds = timeInterval * 60 * 60;
                break;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit. Use SECONDS, MINUTES, or HOURS.");
        }
        return incrAndGetCounter(key, timeInterval, timeUnit, expirationTimeInSeconds);
    }

    /**
     * 增加并返回计数
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit, int expirationTimeInSeconds) {
        if (StrUtil.isBlank(key)) {
            return 0;
        }

        // 根据时间粒度生成 redisKey
        String redisKey = generateRedisKey(key, timeInterval, timeUnit);

        // 更新本地缓存中的计数
        counterCache.asMap().compute(redisKey, (k, v) -> v == null ? 1 : v + 1);

        // 优先从本地缓存中读取数据
        Long localCount = Long.valueOf(counterCache.getIfPresent(redisKey));
        if (localCount != null) {
            return localCount;
        }

        // 如果本地缓存中没有数据，从 Redis 中加载
        long redisCount = redissonClient.getAtomicLong(redisKey).get();
        counterCache.put(redisKey, (int) redisCount); // 更新本地缓存

        // 返回 Redis 中的计数值
        return redisCount;
    }

    /**
     * 生成 Redis 键
     */
    private String generateRedisKey(String key, int timeInterval, TimeUnit timeUnit) {
        long timeFactor;
        switch (timeUnit) {
            case SECONDS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval;
                break;
            case MINUTES:
                timeFactor = Instant.now().getEpochSecond() / 60 / timeInterval;
                break;
            case HOURS:
                timeFactor = Instant.now().getEpochSecond() / 3600 / timeInterval;
                break;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit. Use SECONDS, MINUTES, or HOURS.");
        }
        return key + ":" + timeFactor;
    }

    /**
     * 初始化方法：启动定时同步任务
     */
    @PostConstruct
    public void init() {
        scheduleSyncToRedis();
    }

    /**
     * 定时同步任务：每 10 秒同步一次本地缓存到 Redis
     */
    private void scheduleSyncToRedis() {
        scheduler.scheduleAtFixedRate(() -> {
            // 批量处理本地缓存中的数据
            Map<String, Integer> batch = new HashMap<>(counterCache.asMap());
            if (!batch.isEmpty()) {
                // 将批处理任务拆分为多个小批次
                int batchSize = 100; // 每个批次的大小
                int totalSize = batch.size();
                for (int i = 0; i < totalSize; i += batchSize) {
                    Map<String, Integer> subBatch = new HashMap<>();
                    int end = Math.min(i + batchSize, totalSize);
                    int index = 0;
                    for (Map.Entry<String, Integer> entry : batch.entrySet()) {
                        if (index >= i && index < end) {
                            subBatch.put(entry.getKey(), entry.getValue());
                        }
                        index++;
                    }

                    // 提交小批次任务到线程池
                    try {
                        semaphore.acquire(); // 获取信号量
                        CompletableFuture.runAsync(() -> {
                            try {
                                subBatch.forEach(this::syncToRedis);
                            } finally {
                                semaphore.release(); // 释放信号量
                            }
                        }, customExecutor).exceptionally(ex -> {
                            log.error("Exception occurred while syncing sub-batch to Redis", ex);
                            semaphore.release(); // 释放信号量
                            return null;
                        });
                    } catch (InterruptedException ex) {
                        log.error("Failed to acquire semaphore", ex);
                    }
                }
            }
        }, 0, 10, TimeUnit.SECONDS); // 每 10 秒执行一次
    }

    /**
     * 同步本地缓存到 Redis
     */
    private void syncToRedis(String key, Integer value) {
        try {
            RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
            script.eval(
                    RScript.Mode.READ_WRITE,
                    LUA_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(key),
                    value,  // ARGV[1]: 本地缓存中的值
                    60      // ARGV[2]: 过期时间（秒）
            );
            log.info("Synced key: {}, value: {} to Redis", key, value);
        } catch (Exception ex) {
            log.error("Failed to sync key: {} to Redis", key, ex);
        }
    }

    /**
     * 销毁方法：关闭线程池和调度线程池
     */
    @PreDestroy
    public void destroy() {
        customExecutor.shutdown();
        scheduler.shutdown();
        try {
            if (!customExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                customExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            log.error("Failed to shutdown thread pools", ex);
        }
    }
}
