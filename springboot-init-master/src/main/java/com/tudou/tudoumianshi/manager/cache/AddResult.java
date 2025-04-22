package com.tudou.tudoumianshi.manager.cache;

import lombok.Getter;

/**
 * 添加操作的结果
 */
public class AddResult {
    @Getter
    private final String expelled;
    private final boolean isHotKey;
    @Getter
    private final String key;

    public AddResult(String expelled, boolean isHotKey, String key) {
        this.expelled = expelled;
        this.isHotKey = isHotKey;
        this.key = key;
    }

    public boolean isHotKey() {
        return isHotKey;
    }

}
