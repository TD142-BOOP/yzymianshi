package com.tudou.tudoumianshi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.constant.ThumbConstant;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.manager.cache.CacheManager;
import com.tudou.tudoumianshi.manager.cache.ThumbCacheManager;
import com.tudou.tudoumianshi.mapper.ThumbMapper;
import com.tudou.tudoumianshi.model.dto.thumb.DoThumbRequest;
import com.tudou.tudoumianshi.model.entity.Question;
import com.tudou.tudoumianshi.model.entity.Thumb;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.service.QuestionService;
import com.tudou.tudoumianshi.service.ThumbService;
import com.tudou.tudoumianshi.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 点赞记录表服务实现
 *
 */
@Service("thumbServiceLocalCache")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    // 引入缓存管理
    private final CacheManager cacheManager;

    @Resource
    private ThumbCacheManager thumbCacheManager;

    @Resource
    private QuestionService questionService;


    private final TransactionTemplate transactionTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getQuestionId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        String lockKey = "question:thumb:" + doThumbRequest.getQuestionId() + ":" + loginUser.getId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁，最多等待3秒，锁过期时间10秒
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统繁忙，请稍后重试");
            }

            return transactionTemplate.execute(status -> {
                Long questionId = doThumbRequest.getQuestionId();

                // 检查是否已点赞（幂等性校验）
                boolean exists = this.hasThumb(questionId,loginUser.getId());
                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿重复点赞");
                }

                // 更新点赞数
                boolean update = questionService.lambdaUpdate()
                        .eq(Question::getId, questionId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                // 记录点赞关系
                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setQuestionId(questionId);
                boolean success = update && this.save(thumb);

                // 点赞记录存入 Redis
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = questionId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);

                    // 更新本地缓存中的点赞记录
                    thumbCacheManager.updateThumbCache(questionId, loginUser.getId(), true);
                    // 尝试缓存该题目的点赞关系（如果是热点）
                    thumbCacheManager.cacheThumbRelations(questionId);
                }

                // 更新成功才执行
                return success;

            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        if (doThumbRequest == null || doThumbRequest.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }

        User loginUser = userService.getLoginUser(request);
        String lockKey = "question:thumb:cancel:" + doThumbRequest.getQuestionId() + ":" + loginUser.getId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁（等待3秒，锁持有10秒）
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统繁忙，请稍后重试");
            }

            return transactionTemplate.execute(status -> {
                Long questionId = doThumbRequest.getQuestionId();

                // 查询点赞记录（加锁后再次校验）
                Long thumbId = ((Long) redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), questionId.toString()));
                if (thumbId == null) {
                    throw new RuntimeException("用户未点赞");
                }

                // 更新点赞数
                boolean update = questionService.lambdaUpdate()
                        .eq(Question::getId, questionId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 原子操作：减少点赞数 + 删除记录
                boolean success = update && this.removeById(thumbId);

                // 点赞记录从 Redis 删除
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = questionId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);

                    // 更新本地缓存中的点赞记录
                    thumbCacheManager.updateThumbCache(questionId, loginUser.getId(), false);
                    // 尝试缓存该题目的点赞关系（如果是热点）
                    thumbCacheManager.cacheThumbRelations(questionId);
                }

                return success;

            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            // 确保只有持有锁的线程能释放锁
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean hasThumb(Long questionId, Long userId) {
        // 1. 首先尝试从本地缓存中判断
        Boolean cachedResult = thumbCacheManager.hasThumbInCache(questionId, userId);
        if (cachedResult != null) {
            // 本地缓存命中，直接返回结果
            return cachedResult;
        }

        // 2. 本地缓存未命中，再查Redis
        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, questionId.toString());
        if (thumbIdObj == null) {
            // 尝试缓存该题目的点赞关系（如果是热点）
            thumbCacheManager.cacheThumbRelations(questionId);
            return false;
        }

        // 3. Redis有数据，判断是否点赞
        Long thumbId = (Long) thumbIdObj;
        boolean hasThumb = !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);

        // 4. 尝试缓存该题目的点赞关系（如果是热点）
        thumbCacheManager.cacheThumbRelations(questionId);

        return hasThumb;
    }
}
