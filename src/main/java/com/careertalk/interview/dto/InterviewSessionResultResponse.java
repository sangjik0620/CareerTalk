package com.careertalk.interview.dto;

import lombok.*;
import java.util.List;

@Builder
public record InterviewSessionResultResponse(
        Long sessionId,
        String title,
        String status,
        String mode,
        Integer overallScore,
        String strengths,
        String weaknesses,
        String nextActions,
        List<TurnItem> turns
) {
    public record TurnItem(
            Integer turnNo,
            String aiQuestion,
            String audioUrl
    ) {}
}