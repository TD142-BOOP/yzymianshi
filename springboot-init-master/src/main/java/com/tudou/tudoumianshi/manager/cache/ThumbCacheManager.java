package com.tudou.tudoumianshi.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tudou.tudoumianshi.mapper.ThumbMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 题目点赞缓存管理
 */
@Component
@Slf4j
public class ThumbCacheManager {
    
    @Resource
    private ThumbMapper thumbMapper;
    
    @Resource
    private TopK hotKeyDetector;
    
    private Cache<String, Set<Long>> thumbCache;
    
    @Bean
    public Cache<String, Set<Long>> thumbCache() {
        // 使用明确的类型参数，避免JDK 10+的var类型推断
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        return thumbCache = builder
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .<String, Set<Long>>build();
    }
    
    /**
     * 将热点题目的点赞记录加载到本地缓存
     * @param questionId 题目ID
     */
    public void cacheThumbRelations(Long questionId) {
        String key = "question:thumb:" + questionId;
        
        // 检查是否需要缓存（是否是热点）
        AddResult result = hotKeyDetector.add(key, 1);
        if (result.isHotKey()) {
            // 已经缓存过就不再重复加载
            if (thumbCache.getIfPresent(key) != null) {
                return;
            }
            
            // 查询该题目所有点赞用户ID
            List<Long> userIds = thumbMapper.getThumbUserIdsByQuestionId(questionId);
            if (userIds != null && !userIds.isEmpty()) {
                // 明确指定Set的类型
                Set<Long> userIdSet = new HashSet<Long>(userIds);
                thumbCache.put(key, userIdSet);
                log.info("缓存热点题目点赞关系: questionId={}, 点赞用户数={}", questionId, userIds.size());
            }
        }
    }
    
    /**
     * 从本地缓存判断用户是否点赞过题目
     * @param questionId 题目ID
     * @param userId 用户ID
     * @return 是否点赞
     */
    public Boolean hasThumbInCache(Long questionId, Long userId) {
        String key = "question:thumb:" + questionId;
        Set<Long> userIdSet = thumbCache.getIfPresent(key);
        
        if (userIdSet != null) {
            boolean hasThumb = userIdSet.contains(userId);
            log.info("从本地缓存判断点赞状态: userId={}, questionId={}, hasThumb={}", userId, questionId, hasThumb);
            return hasThumb;
        }
        
        return null; // 缓存未命中
    }
    
    /**
     * 更新本地缓存中的点赞记录
     * @param questionId 题目ID
     * @param userId 用户ID
     * @param isAdd 是否是添加点赞
     */
    public void updateThumbCache(Long questionId, Long userId, boolean isAdd) {
        String key = "question:thumb:" + questionId;
        Set<Long> userIdSet = thumbCache.getIfPresent(key);
        
        if (userIdSet != null) {
            // 本地有缓存，更新缓存
            if (isAdd) {
                userIdSet.add(userId);
                log.info("本地缓存添加点赞关系: userId={}, questionId={}", userId, questionId);
            } else {
                userIdSet.remove(userId);
                log.info("本地缓存移除点赞关系: userId={}, questionId={}", userId, questionId);
            }
            thumbCache.put(key, userIdSet);
        }
    }
} 