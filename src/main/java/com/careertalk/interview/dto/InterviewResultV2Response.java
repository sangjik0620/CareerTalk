package com.careertalk.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InterviewResultV2Response(
        Long sessionId,
        String analysisStatus,
        LocalDateTime generatedAt,
        JsonNode evaluation,
        List<TurnDetail> turns
) {
    public record TurnDetail(
            Long turnId,
            Integer turnNo,
            String question,
            String sttStatus,
            String sttText,
            Audio audio,
            Metrics metrics,
            Scores scores,
            Feedback feedback
    ) {}

    public record Audio(
            Long fileId,
            String audioUrl,
            Integer durationSec
    ) {}

    public record Metrics(
            Map<String, Object> audioMetrics,
            Map<String, Object> pythonExtracted
    ) {}

    public record Scores(
            Integer tremorRiskScore,
            Integer confidenceScore,
            Integer fluencyScore,
            Integer overallVoiceScore,
            String overallGrade,
            Double overallReliability,
            List<String> flags,
            Map<String, Object> raw
    ) {}

    public record Feedback(
            Integer score,
            String oneLineFeedback,
            String fullFeedback,
            Integer sentimentScore,
            Keywords keywords,
            VoiceFeedback voice
    ) {}

    public record Keywords(
            List<String> technical,
            List<String> soft,
            List<String> company
    ) {}

    public record VoiceFeedback(
            Integer overallVoiceScore,
            Integer confidenceScore,
            Integer fluencyScore,
            Integer tremorRiskScore,
            List<String> strengths,
            List<String> weaknesses
    ) {}
}