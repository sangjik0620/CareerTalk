package com.careertalk.chatbot.service;

import com.careertalk.analysis.common.service.OpenAiService;
import com.careertalk.chatbot.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final OpenAiService openAiService;


    @Value("classpath:prompts/chatbot-guide.txt")
    private Resource chatbotPromptResource;

    public ChatResponse getChatbotReply(String userMessage) {
        try {

            String systemPrompt = FileCopyUtils.copyToString(
                    new InputStreamReader(chatbotPromptResource.getInputStream(), StandardCharsets.UTF_8)
            );

            String aiReply = openAiService.getChatbotResponse(systemPrompt, userMessage);

            return new ChatResponse(true, aiReply, null);

        } catch (Exception e) {
            log.error("챗봇 프롬프트 로드 중 에러: {}", e.getMessage());
            return new ChatResponse(false, null, "안내 로봇이 잠시 자리를 비웠어요. ");
        }
    }
}