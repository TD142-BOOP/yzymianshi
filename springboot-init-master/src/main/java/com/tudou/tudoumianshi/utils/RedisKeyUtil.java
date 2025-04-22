package com.tudou.tudoumianshi.utils;


import com.tudou.tudoumianshi.constant.ThumbConstant;


/**
 * @author pine
 */
public class RedisKeyUtil {

    public static String getUserThumbKey(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取 临时点赞记录 key
     */
    public static String getTempThumbKey(String time) {
        return String.format(ThumbConstant.TEMP_THUMB_KEY_PREFIX, time);
    }

}
