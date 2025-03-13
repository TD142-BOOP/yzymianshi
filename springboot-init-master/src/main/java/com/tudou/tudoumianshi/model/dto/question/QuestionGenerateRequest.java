package com.tudou.tudoumianshi.model.dto.question;

import lombok.Data;

import java.io.Serializable;


@Data
public class QuestionGenerateRequest implements Serializable {

    /**
     * 题目类型
     */
    private String questionType;
    /**
     * 题目数量
     */
    private int questionNum=10;

    private static final long serialVersionUID = 1L;
}
