package com.tudou.tudoumianshi.job.cycle;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tudou.tudoumianshi.mapper.QuestionMapper;
import com.tudou.tudoumianshi.model.entity.Thumb;
import com.tudou.tudoumianshi.model.enums.ThumbTypeEnum;
import com.tudou.tudoumianshi.service.ThumbService;
import com.tudou.tudoumianshi.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库
 *
 */
//@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbService thumbService;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {
        log.info("开始执行");
        DateTime nowDate = DateUtil.date();
        String date = DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10 - 1) * 10;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }

    public void syncThumb2DBByDate(String date) {
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);

        if (CollUtil.isEmpty(allTempThumbMap)) {
            log.info("没有临时点赞数据需要同步");
            return;
        }

        Map<Long, Long> questionThumbCountMap = new HashMap<>();
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;

        for (Object userIdQuestionIdObj : allTempThumbMap.keySet()) {
            String userIdQuestionId = (String) userIdQuestionIdObj;
            String[] userIdAndQuestionId = userIdQuestionId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndQuestionId[0]);
            Long questionId = Long.valueOf(userIdAndQuestionId[1]);
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdQuestionId).toString());

            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setQuestionId(questionId);
                thumbList.add(thumb);
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                needRemove = true;
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getQuestionId, questionId);
            } else if (thumbType != ThumbTypeEnum.NON.getValue()) {
                log.warn("数据异常：{}", userId + "," + questionId + "," + thumbType);
            }
            questionThumbCountMap.put(questionId, questionThumbCountMap.getOrDefault(questionId, 0L) + thumbType);
        }

        // 批量操作
        thumbService.saveBatch(thumbList);
        if (needRemove) {
            thumbService.remove(wrapper);
        }
        if (!questionThumbCountMap.isEmpty()) {
            log.debug("准备批量更新点赞数: {}", questionThumbCountMap);
            try {
                questionMapper.batchUpdateThumbCount(questionThumbCountMap);
                log.debug("批量更新点赞数成功");
            } catch (Exception e) {
                log.error("批量更新点赞数失败", e);
            }
        }

        // ✅ JDK 8 兼容的异步删除（方案1）
        CompletableFuture.runAsync(() -> {
            redisTemplate.delete(tempThumbKey);
        });
    }
}
