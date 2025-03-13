package com.tudou.tudoumianshi.manager;


import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.aspectj.bridge.Message;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;


@Service
public class AiManager {

    @Resource
    private ArkService aiService;

    private final String DEFAULT_MODEL = "deepseek-r1-250120";

    public String doChat(String userPrompt){
        return doChat(userPrompt,DEFAULT_MODEL,"");
    }

    public String doChat(List<ChatMessage> messages){
        return doChat(messages,DEFAULT_MODEL);
    }

    public String doChat(List<ChatMessage> messages,String model){
        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();
        List<ChatCompletionChoice> choices=aiService.createChatCompletion(streamChatCompletionRequest).getChoices();
        if(CollectionUtils.isNotEmpty(choices)){
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI调用失败");
    }

    public String doChat(String userPrompt,String systemPrompt){
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model(DEFAULT_MODEL)
                .messages(messages)
                .build();
        List<ChatCompletionChoice> choices=aiService.createChatCompletion(streamChatCompletionRequest).getChoices();
        if(CollectionUtils.isNotEmpty(choices)){
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI调用失败");
    }

    public String doChat(String userPrompt,String model,String systemPrompt){
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();
        List<ChatCompletionChoice> choices=aiService.createChatCompletion(streamChatCompletionRequest).getChoices();
        if(CollectionUtils.isNotEmpty(choices)){
              return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI调用失败");
    }
}
