package com.tudou.tudoumianshi.service.impl;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.constant.CommonConstant;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.manager.AiManager;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewAddRequest;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewChatMessage;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewEventRequest;
import com.tudou.tudoumianshi.model.dto.mockinterview.MockInterviewQueryRequest;
import com.tudou.tudoumianshi.model.entity.MockInterview;
import com.tudou.tudoumianshi.mapper.MockInterviewMapper;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.model.enums.MockInterviewEventEnum;
import com.tudou.tudoumianshi.model.enums.MockInterviewStatusEnum;
import com.tudou.tudoumianshi.service.MockInterviewService;
import com.tudou.tudoumianshi.utils.SqlUtils;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @description 针对表【mock_interview(模拟面试)】的数据库操作Service实现
* @createDate 2025-03-07 16:57:40
*/
@Service
public class MockInterviewServiceImpl extends ServiceImpl<MockInterviewMapper, MockInterview>
    implements MockInterviewService {

    @Resource
    private AiManager aiManager;


    @Override
    public Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User user) {
        if(mockInterviewAddRequest == null || user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String workExperience = mockInterviewAddRequest.getWorkExperience();
        String jobPosition = mockInterviewAddRequest.getJobPosition();
        String difficulty = mockInterviewAddRequest.getDifficulty();
        ThrowUtils.throwIf(StrUtil.hasBlank(workExperience,jobPosition,difficulty),ErrorCode.PARAMS_ERROR,"参数错误");

        MockInterview mockInterview = new MockInterview();
        mockInterview.setUserId(user.getId());
        mockInterview.setWorkExperience(workExperience);
        mockInterview.setJobPosition(jobPosition);
        mockInterview.setDifficulty(difficulty);
        mockInterview.setStatus(MockInterviewStatusEnum.TO_START.getValue());
        boolean result = this.save(mockInterview);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"创建失败");
        return mockInterview.getId();
    }

    /**
     * 获取查询条件
     * @param mockInterviewQueryRequest
     * @return
     */
    @Override
    public Wrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest) {
        QueryWrapper<MockInterview> queryWrapper = new QueryWrapper<>();
        if(mockInterviewQueryRequest==null){
            return queryWrapper;
        }
        Long id = mockInterviewQueryRequest.getId();
        String workExperience = mockInterviewQueryRequest.getWorkExperience();
        String jobPosition = mockInterviewQueryRequest.getJobPosition();
        String difficulty = mockInterviewQueryRequest.getDifficulty();
        Long userId = mockInterviewQueryRequest.getUserId();
        String sortField = mockInterviewQueryRequest.getSortField();
        String sortOrder = mockInterviewQueryRequest.getSortOrder();
        Integer status = mockInterviewQueryRequest.getStatus();
        //补充信息
        queryWrapper.eq(ObjectUtils.isNotEmpty(id),"id",id);;
        queryWrapper.like(StrUtil.isNotBlank(workExperience),"work_experience",workExperience);
        queryWrapper.like(StrUtil.isNotBlank(jobPosition),"job_position",jobPosition);
        queryWrapper.like(StrUtil.isNotBlank(difficulty),"difficulty",difficulty);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId),"user_id",userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status),"status",status);
        //排序
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),sortOrder.equals(CommonConstant.SORT_ORDER_ASC),sortField);
        return queryWrapper;
    }

    /**
     * 处理模拟面试事件
     * @param mockInterviewEventRequest
     * @param loginUser
     * @return
     */
    @Override
    public String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser) {
        Long id = mockInterviewEventRequest.getId();
        if(id==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        MockInterview mockInterview = this.getById(id);
        ThrowUtils.throwIf(mockInterview==null,ErrorCode.NOT_FOUND_ERROR,"模拟面试不存在");
        if(mockInterview.getId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有权限");
        }
        String event = mockInterviewEventRequest.getEvent();
        MockInterviewEventEnum eventEnum = MockInterviewEventEnum.getEnumByValue(event);
        switch (eventEnum){
            case START:
                return  handleChatStartEvent(mockInterview);
            case CHAT:
                return  handleChatEventMessage(mockInterview,mockInterviewEventRequest);
            case END:
                return  handleChatEndEvent(mockInterview);
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数错误");
        }
    }

    /**
     * 处理结束聊天事件
     * @param mockInterview
     * @return
     */
    private String handleChatEndEvent(MockInterview mockInterview) {
        String messages = mockInterview.getMessages();
        List<MockInterviewChatMessage> historyChatMessageList = JSONUtil.parseArray(messages).toList(MockInterviewChatMessage.class);
        final List<ChatMessage> chatMessageList = transformToChatMessage(historyChatMessageList);
        // 调用 AI 获取结果
        String userPrompt = "结束";
        ChatMessage endChatMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        chatMessageList.add(endChatMessage);
        String chatAnswer = aiManager.doChat(chatMessageList);
        ChatMessage chatMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(chatAnswer).build();
        chatMessageList.add(chatMessage);
        //保存记录并更新状态
        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(chatMessageList);
        String jsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);
        MockInterview newUpdateMockInterview = new MockInterview();
        newUpdateMockInterview.setId(mockInterview.getId());
        newUpdateMockInterview.setMessages(jsonStr);
        newUpdateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        boolean result = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新失败");
        return chatAnswer;
    }


    /**
     * 处理历史聊天事件
     * @param mockInterview
     * @param mockInterviewEventRequest
     * @return
     */
    private String handleChatEventMessage(MockInterview mockInterview, MockInterviewEventRequest mockInterviewEventRequest) {
        String message = mockInterviewEventRequest.getMessage();
        // 构造消息列表，注意需要先获取之前的消息记录
        String historyMessage = mockInterview.getMessages();
        List<MockInterviewChatMessage> historyMessageList = JSONUtil.parseArray(historyMessage).toList(MockInterviewChatMessage.class);
        final List<ChatMessage> chatMessages = transformToChatMessage(historyMessageList);
        final ChatMessage chatUserMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(message).build();
        chatMessages.add(chatUserMessage);
        // 调用 AI 获取结果
        String chatAnswer = aiManager.doChat(chatMessages);
        ChatMessage chatAssistantMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(chatAnswer).build();
        chatMessages.add(chatAssistantMessage);
        // 保存消息记录，并且更新状态
        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(chatMessages);
        String newJsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);
        MockInterview newUpdateMockInterview = new MockInterview();
        newUpdateMockInterview.setId(mockInterview.getId());
        newUpdateMockInterview.setMessages(newJsonStr);
        // 如果 AI 主动结束了面试，更改状态
        if (chatAnswer.contains("【面试结束】")) {
            newUpdateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        }
        boolean newResult = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!newResult, ErrorCode.SYSTEM_ERROR, "更新失败");
        return chatAnswer;

    }

    /**
     * 处理开始聊天事件
     * @param mockInterview
     * @return
     */
    private String handleChatStartEvent(MockInterview mockInterview) {
        String systemPrompt = String.format("你是一位严厉的程序员面试官，我是候选人，来应聘 %s 的 %s 岗位，面试难度为 %s。请你向我依次提出问题（最多 20 个问题），我也会依次回复。在这期间请完全保持真人面试官的口吻，比如适当引导学员、或者表达出你对学员回答的态度。\n" +
                "必须满足如下要求：\n" +
                "1. 当学员回复 “开始” 时，你要正式开始面试\n" +
                "2. 当学员表示希望 “结束面试” 时，你要结束面试\n" +
                "3. 此外，当你觉得这场面试可以结束时（比如候选人回答结果较差、不满足工作年限的招聘需求、或者候选人态度不礼貌），必须主动提出面试结束，不用继续询问更多问题了。并且要在回复中包含字符串【面试结束】\n" +
                "4. 面试结束后，应该给出候选人整场面试的表现和总结。", mockInterview.getWorkExperience(), mockInterview.getJobPosition(), mockInterview.getDifficulty());
        String userPrompt = "开始";
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        String chatAnswer = aiManager.doChat(messages);
        ChatMessage chatMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(chatAnswer).build();
        messages.add(chatMessage);
        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(messages);
        String jsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);

        MockInterview updateMockInterview = new MockInterview();
        updateMockInterview.setId(mockInterview.getId());
        updateMockInterview.setMessages(jsonStr);
        updateMockInterview.setStatus(MockInterviewStatusEnum.IN_PROGRESS.getValue());
        boolean result = this.updateById(updateMockInterview);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新失败");
        return chatAnswer;
    }
    /**
     * 消息记录对象转换
     *
     * @param chatMessageList
     * @return
     */
    List<MockInterviewChatMessage> transformFromChatMessage(List<ChatMessage> chatMessageList) {
        return chatMessageList.stream().map(chatMessage -> {
            MockInterviewChatMessage mockInterviewChatMessage = new MockInterviewChatMessage();
            mockInterviewChatMessage.setRole(chatMessage.getRole().value());
            mockInterviewChatMessage.setMessage(chatMessage.getContent().toString());
            return mockInterviewChatMessage;
        }).collect(Collectors.toList());
    }

    /**
     * 消息记录对象转换
     *
     * @param chatMessageList
     * @return
     */
    List<ChatMessage> transformToChatMessage(List<MockInterviewChatMessage> chatMessageList) {
        return chatMessageList.stream().map(chatMessage -> {
            return ChatMessage.builder().role(ChatMessageRole.valueOf(StringUtils.upperCase(chatMessage.getRole())))
                    .content(chatMessage.getMessage()).build();
        }).collect(Collectors.toList());
    }

}




