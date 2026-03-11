package com.careertalk.chatbot.controller;

import com.careertalk.chatbot.dto.ChatRequest;
import com.careertalk.chatbot.dto.ChatResponse;
import com.careertalk.chatbot.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;


    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {

        ChatResponse response = chatService.getChatbotReply(request.getMessage());

        return ResponseEntity.ok(response);
    }
}
