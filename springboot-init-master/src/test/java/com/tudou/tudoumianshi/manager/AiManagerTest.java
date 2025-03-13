package com.tudou.tudoumianshi.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AiManagerTest {


    @Resource
    private AiManager aiManager;
    @Test
    void doChat() {
        String string = aiManager.doChat("你好");
        System.out.println(string);
    }

    @Test
    void testDoChat() {
        String string = aiManager.doChat("你好", "deepseek-r1-250120", "当我向你发送你好时，回答我不好");
        System.out.println(string);
    }
}
