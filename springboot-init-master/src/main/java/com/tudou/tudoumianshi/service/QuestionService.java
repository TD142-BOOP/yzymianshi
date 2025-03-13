package com.tudou.tudoumianshi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tudou.tudoumianshi.model.dto.question.QuestionQueryRequest;
import com.tudou.tudoumianshi.model.entity.Question;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.model.vo.QuestionVO;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题目服务
 *

 */
public interface QuestionService extends IService<Question> {

    /**
     * 校验数据
     *
     * @param question
     * @param add 对创建的数据进行校验
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

    /**
     * 获取题目列表
     * @param questionQueryRequest
     * @return
     */
    Page<Question> listQuestionQueryByPage(QuestionQueryRequest questionQueryRequest);

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);


    @Transactional(rollbackFor = Exception.class)
    void batchDeleteQuestionToBank(List<Long> questionIds);


    /**
     * ai生成题目
     * @param questionType
     * @param questionNum
     * @param user
     * @return
     */
    Boolean aiGenerateQuestions(String questionType, Integer questionNum, User user);
}
