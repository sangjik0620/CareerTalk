package com.careertalk.interview.dto;

import java.util.List;

public record InterviewSessionResultResponse(
        Long sessionId,
        String title,
        String status,
        String mode,
        List<TurnItem> turns
) {
    public record TurnItem(
            Integer turnNo,
            String question,
            String audioUrl
    ) {}
}