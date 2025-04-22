package com.tudou.tudoumianshi.manager;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Pulsar 事件：用于本地计数同步
 */
@Data
@NoArgsConstructor      // 生成无参构造器，供 Jackson 反序列化使用
@AllArgsConstructor
public class CounterEvent implements Serializable {
    private String key;
    private Integer value;
}
