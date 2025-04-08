package com.tudou.tudoumianshi.manager;

import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@Service
public class HotKeyCounterManager {

    /**
     * 增加并返回计数
     */
    public Boolean incrAndGetCounter(String key) {
        // 根据时间粒度生成 redisKey
        String redisKey ="anti_crawler_" + key;
        // 如果是热 key
        return JdHotKeyStore.isHotKey(redisKey);
    }
}
