package com.careertalk.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InterviewResultV2Response(
        Long sessionId,
        String analysisStatus,          // PENDING/PROCESSING/DONE/FAILED
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
            Scores scores
    ) {}

    public record Audio(
            Long fileId,
            String audioUrl,
            Integer durationSec
    ) {}

    public record Metrics(
            Map<String, Object> audioMetrics,         // audio_metrics_json 전체(또는 필요한 일부)
            Map<String, Object> pythonExtracted       // python_metrics_json.extracted
    ) {}

    public record Scores(
            Integer tremorRiskScore,
            Integer confidenceScore,
            Integer fluencyScore,
            Integer overallVoiceScore,
            String overallGrade,
            Double overallReliability,
            List<String> flags,
            Map<String, Object> raw                    // 필요하면 full raw scores (선택)
    ) {}
}