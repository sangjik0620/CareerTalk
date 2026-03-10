package com.careertalk.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatResponse {
    private boolean success;
    private String reply;
    private String errorMessage;
}
