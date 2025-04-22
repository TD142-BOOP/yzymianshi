package com.tudou.tudoumianshi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tudou.tudoumianshi.constant.RedisLuaScriptConstant;
import com.tudou.tudoumianshi.listener.thumb.ThumbEvent;
import com.tudou.tudoumianshi.mapper.ThumbMapper;
import com.tudou.tudoumianshi.model.dto.thumb.DoThumbRequest;
import com.tudou.tudoumianshi.model.entity.Thumb;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.model.enums.LuaStatusEnum;
import com.tudou.tudoumianshi.service.ThumbService;
import com.tudou.tudoumianshi.service.UserService;
import com.tudou.tudoumianshi.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service("thumbService")
@Slf4j
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private PulsarClient pulsarClient;

    private Producer<byte[]> thumbProducer;

    private static final String THUMB_TOPIC = "thumb-topic";

    /**
     * 初始化Pulsar Producer
     */
    @PostConstruct
    public void init() {
        try {
            if (pulsarClient != null) {
                thumbProducer = pulsarClient.newProducer()
                        .topic(THUMB_TOPIC)
                        .create();
                log.info("Pulsar点赞生产者初始化成功");
            }
        } catch (Exception e) {
            log.error("Pulsar点赞生产者初始化失败", e);
        }
    }

    /**
     * 关闭资源
     */
    @PreDestroy
    public void destroy() {
        try {
            if (thumbProducer != null) {
                thumbProducer.close();
                log.info("Pulsar点赞生产者已关闭");
            }
        } catch (Exception e) {
            log.error("关闭Pulsar生产者失败", e);
        }
    }

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getQuestionId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        final Long loginUserId = loginUser.getId();
        final Long questionId = doThumbRequest.getQuestionId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);

        // 明确List的类型以避免类型推断问题
        List<String> keys = new ArrayList<String>();
        keys.add(userThumbKey);

        // 执行 Lua 脚本，点赞存入 Redis
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                keys,
                questionId
        );
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户已点赞");
        }

        // 使用传统方式创建ThumbEvent对象，避免使用构建器模式
        final ThumbEvent thumbEvent = new ThumbEvent();
        thumbEvent.setQuestionId(questionId);
        thumbEvent.setUserId(loginUserId);
        thumbEvent.setType(ThumbEvent.EventType.INCR);
        thumbEvent.setEventTime(LocalDateTime.now());

        // 使用原生Pulsar API发送消息
        if (thumbProducer != null) {
            try {
                // 将事件转换为JSON字符串
                String eventJson = convertEventToJson(thumbEvent);
                // 异步发送消息
                CompletableFuture<MessageId> future = thumbProducer.sendAsync(eventJson.getBytes());

                // 添加回调处理结果
                future.whenComplete(new java.util.function.BiConsumer<MessageId, Throwable>() {
                    @Override
                    public void accept(MessageId messageId, Throwable throwable) {
                        if (throwable != null) {
                            // 发送失败，回滚Redis
                            redisTemplate.opsForHash().delete(userThumbKey, questionId.toString());
                            log.error("点赞事件发送失败: userId={}, questionId={}", loginUserId, questionId, throwable);
                        } else {
                            log.info("点赞事件发送成功: userId={}, questionId={}", loginUserId, questionId);
                        }
                    }
                });
            } catch (Exception e) {
                // 处理异常，回滚Redis状态
                redisTemplate.opsForHash().delete(userThumbKey, questionId.toString());
                log.error("点赞事件发送异常: userId={}, questionId={}", loginUserId, questionId, e);
            }
        } else {
            log.warn("Pulsar生产者未初始化，无法发送点赞事件");
        }

        return true;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getQuestionId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        final Long loginUserId = loginUser.getId();
        final Long questionId = doThumbRequest.getQuestionId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);

        // 明确List的类型以避免类型推断问题
        List<String> keys = new ArrayList<String>();
        keys.add(userThumbKey);

        // 执行 Lua 脚本，点赞记录从 Redis 删除
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                keys,
                questionId
        );
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户未点赞");
        }

        // 使用传统方式创建ThumbEvent对象，避免使用构建器模式
        final ThumbEvent thumbEvent = new ThumbEvent();
        thumbEvent.setQuestionId(questionId);
        thumbEvent.setUserId(loginUserId);
        thumbEvent.setType(ThumbEvent.EventType.DECR);
        thumbEvent.setEventTime(LocalDateTime.now());

        // 使用原生Pulsar API发送消息
        if (thumbProducer != null) {
            try {
                // 将事件转换为JSON字符串
                String eventJson = convertEventToJson(thumbEvent);
                // 异步发送消息
                CompletableFuture<MessageId> future = thumbProducer.sendAsync(eventJson.getBytes());

                // 添加回调处理结果
                future.whenComplete(new java.util.function.BiConsumer<MessageId, Throwable>() {
                    @Override
                    public void accept(MessageId messageId, Throwable throwable) {
                        if (throwable != null) {
                            // 发送失败，回滚Redis
                            redisTemplate.opsForHash().put(userThumbKey, questionId.toString(), true);
                            log.error("取消点赞事件发送失败: userId={}, questionId={}", loginUserId, questionId, throwable);
                        } else {
                            log.info("取消点赞事件发送成功: userId={}, questionId={}", loginUserId, questionId);
                        }
                    }
                });
            } catch (Exception e) {
                // 处理异常，回滚Redis状态
                redisTemplate.opsForHash().put(userThumbKey, questionId.toString(), true);
                log.error("取消点赞事件发送异常: userId={}, questionId={}", loginUserId, questionId, e);
            }
        } else {
            log.warn("Pulsar生产者未初始化，无法发送取消点赞事件");
        }

        return true;
    }

    /**
     * 将ThumbEvent转换为JSON字符串
     */
    private String convertEventToJson(ThumbEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"questionId\":").append(event.getQuestionId()).append(",");
        sb.append("\"userId\":").append(event.getUserId()).append(",");
        sb.append("\"type\":\"").append(event.getType()).append("\",");
        sb.append("\"eventTime\":\"").append(event.getEventTime()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    @Override
    public Boolean hasThumb(Long questionId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), questionId.toString());
    }
}
