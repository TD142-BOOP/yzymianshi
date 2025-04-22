package com.tudou.tudoumianshi.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public interface RedisLuaScriptConstant {

    /**
     * 点赞 Lua 脚本（JDK 8 兼容写法）
     * KEYS[1]       -- 临时计数键
     * KEYS[2]       -- 用户点赞状态键
     * ARGV[1]       -- 用户 ID
     * ARGV[2]       -- 博客 ID
     * 返回:
     * -1: 已点赞
     * 1: 操作成功
     */
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>(
            "local tempThumbKey = KEYS[1];\n" +       // 临时计数键（如 thumb:temp:{timeSlice}）
                    "local userThumbKey = KEYS[2];\n" +       // 用户点赞状态键（如 thumb:{userId}）
                    "local userId = ARGV[1];\n" +             // 用户 ID
                    "local questionId = ARGV[2];\n" +         // 题目 ID
                    "\n" +
                    "-- 1. 检查是否已点赞（避免重复操作）\n" +
                    "if redis.call('HEXISTS', userThumbKey, questionId) == 1 then\n" +
                    "    return -1;\n" +                      // 已点赞，返回 -1 表示失败
                    "end\n" +
                    "\n" +
                    "-- 2. 获取旧值（不存在则默认为 0）\n" +
                    "local hashKey = userId .. ':' .. questionId;\n" +
                    "local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0);\n" +
                    "\n" +
                    "-- 3. 计算新值\n" +
                    "local newNumber = oldNumber + 1;\n" +
                    "\n" +
                    "-- 4. 原子性更新：写入临时计数 + 标记用户已点赞\n" +
                    "redis.call('HSET', tempThumbKey, hashKey, newNumber);\n" +
                    "redis.call('HSET', userThumbKey, questionId, 1);\n" +
                    "\n" +
                    "return 1;",                               // 返回 1 表示成功
            Long.class
    );

    /**
     * 取消点赞 Lua 脚本（JDK 8 兼容写法）
     * 参数同上
     * 返回：
     * -1: 未点赞
     * 1: 操作成功
     */
    public static final RedisScript<Long> UNTHUMB_SCRIPT = new DefaultRedisScript<>(
            "local tempThumbKey = KEYS[1];\n" +       // 临时计数键（如 thumb:temp:{timeSlice}）
                    "local userThumbKey = KEYS[2];\n" +       // 用户点赞状态键（如 thumb:{userId}）
                    "local userId = ARGV[1];\n" +             // 用户 ID
                    "local questionId = ARGV[2];\n" +         // 博客 ID
                    "\n" +
                    "-- 1. 检查用户是否已点赞（若未点赞，直接返回失败）\n" +
                    "if redis.call('HEXISTS', userThumbKey, questionId) ~= 1 then\n" +
                    "    return -1;\n" +                      // 未点赞，返回 -1 表示失败
                    "end\n" +
                    "\n" +
                    "-- 2. 获取当前临时计数（若不存在则默认为 0）\n" +
                    "local hashKey = userId .. ':' .. questionId;\n" +
                    "local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0);\n" +
                    "\n" +
                    "-- 3. 计算新值并更新\n" +
                    "local newNumber = oldNumber - 1;\n" +
                    "\n" +
                    "-- 4. 原子性操作：更新临时计数 + 删除用户点赞标记\n" +
                    "redis.call('HSET', tempThumbKey, hashKey, newNumber);\n" +
                    "redis.call('HDEL', userThumbKey, questionId);\n" +
                    "\n" +
                    "return 1;",                              // 返回 1 表示成功
            Long.class
    );


    /**
     * 点赞 Lua 脚本（JDK 8 兼容）
     * KEYS[1]       -- 用户点赞状态键
     * ARGV[1]       -- 博客 ID
     * 返回:
     * -1: 已点赞
     * 1: 操作成功
     */
    public static final RedisScript<Long> THUMB_SCRIPT_MQ = new DefaultRedisScript<>(
            "local userThumbKey = KEYS[1]\n" +
                    "local questionId = ARGV[1]\n" +
                    "\n" +
                    "-- 判断是否已经点赞\n" +
                    "if redis.call(\"HEXISTS\", userThumbKey, questionId) == 1 then\n" +
                    "    return -1\n" +
                    "end\n" +
                    "\n" +
                    "-- 添加点赞记录\n" +
                    "redis.call(\"HSET\", userThumbKey, questionId, 1)\n" +
                    "return 1",
            Long.class
    );

    /**
     * 取消点赞 Lua 脚本（JDK 8 兼容）
     * KEYS[1]       -- 用户点赞状态键
     * ARGV[1]       -- 博客 ID
     * 返回:
     * -1: 未点赞
     * 1: 操作成功
     */
    public static final RedisScript<Long> UNTHUMB_SCRIPT_MQ = new DefaultRedisScript<>(
            "local userThumbKey = KEYS[1]\n" +
                    "local questionId = ARGV[1]\n" +
                    "\n" +
                    "-- 判断是否已点赞\n" +
                    "if redis.call(\"HEXISTS\", userThumbKey, questionId) == 0 then\n" +
                    "    return -1\n" +
                    "end\n" +
                    "\n" +
                    "-- 删除点赞记录\n" +
                    "redis.call(\"HDEL\", userThumbKey, questionId)\n" +
                    "return 1",
            Long.class
    );
}
