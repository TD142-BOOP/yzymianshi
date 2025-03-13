package com.tudou.tudoumianshi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewAddRequest;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewEventRequest;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewQueryRequest;
import com.tudou.tudoumianshi.model.entity.MockInterview;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tudou.tudoumianshi.model.entity.User;

/**
* @description 针对表【mock_interview(模拟面试)】的数据库操作Service
* @createDate 2025-03-07 16:57:40
*/
public interface MockInterviewService extends IService<MockInterview> {

    /**
     * 创建模拟面试
     * @param mockInterview
     * @param user
     * @return
     */
    Long createMockInterview(MockInterviewAddRequest mockInterview, User user);

    /**
     * 分页查询模拟面试
     * @param mockInterviewQueryRequest
     * @return
     */
    Wrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest);

    /**
     * 处理模拟面试事件
     * @param mockInterviewEventRequest
     * @param loginUser
     * @return
     */
    String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser);
}
