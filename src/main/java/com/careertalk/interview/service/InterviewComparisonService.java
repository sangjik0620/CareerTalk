package com.careertalk.interview.service;

import com.careertalk.interview.dto.CategoryComparisonItem;
import com.careertalk.interview.dto.ComparisonResponse;
import com.careertalk.interview.dto.ScoreHistoryItem;
import com.careertalk.interview.entity.InterviewEvaluation;
import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.repository.InterviewEvaluationRepository;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewComparisonService {

    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private static final int SCORE_HISTORY_LIMIT = 6;

    public ComparisonResponse buildComparison(Long sessionId, JsonNode evaluationJson) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션이 없습니다. sessionId=" + sessionId));

        InterviewEvaluation currentEval = evaluationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("평가 데이터가 없습니다. sessionId=" + sessionId));

        Long userNum = session.getUserNum();
        Integer overallScore = resolveOverallScore(currentEval, evaluationJson);

        List<ScoreHistoryItem> scoreHistory = buildScoreHistory(
                userNum,
                session.getCreatedAt(),
                sessionId
        );

        Integer percentileRank = resolvePercentileRank(sessionId, overallScore);

        Map<String, CategoryComparisonItem> categoryComparison =
                buildCategoryComparison(
                        sessionId,
                        userNum,
                        session.getCreatedAt(),
                        currentEval,
                        evaluationJson
                );

        return ComparisonResponse.builder()
                .percentileRank(percentileRank)
                .scoreHistory(scoreHistory)
                .categoryComparison(categoryComparison)
                .build();
    }

    private Integer resolveOverallScore(InterviewEvaluation currentEval, JsonNode evaluationJson) {
        if (currentEval.getOverallScore() != null) {
            return clamp(currentEval.getOverallScore());
        }

        if (evaluationJson != null) {
            JsonNode summary = evaluationJson.path("summary");
            if (summary.has("overallScore") && summary.get("overallScore").isNumber()) {
                return clamp(summary.get("overallScore").asInt());
            }
            if (evaluationJson.has("overallScore") && evaluationJson.get("overallScore").isNumber()) {
                return clamp(evaluationJson.get("overallScore").asInt());
            }
        }

        return null;
    }

    private Integer resolvePercentileRank(Long sessionId, Integer overallScore) {
        if (overallScore == null) {
            return null;
        }

        Double percentile = evaluationRepository.findPercentileRankExcludingSession(sessionId, overallScore);
        return percentile == null ? null : clamp((int) Math.round(percentile));
    }

    private List<ScoreHistoryItem> buildScoreHistory(Long userNum, LocalDateTime currentCreatedAt, Long currentSessionId) {
        List<Object[]> rows = evaluationRepository.findScoreHistoryUntilCurrent(
                userNum,
                currentCreatedAt,
                SCORE_HISTORY_LIMIT
        );

        List<ScoreHistoryItem> result = new ArrayList<>();

        for (int i = rows.size() - 1; i >= 0; i--) {
            Object[] row = rows.get(i);

            String date = row[0] != null ? String.valueOf(row[0]) : null;
            Integer score = row[1] instanceof Number ? ((Number) row[1]).intValue() : null;
            Long sessionId = row[2] instanceof Number ? ((Number) row[2]).longValue() : null;

            result.add(ScoreHistoryItem.builder()
                    .date(Objects.equals(sessionId, currentSessionId) ? "현재" : date)
                    .score(clamp(score))
                    .build());
        }

        return result;
    }

    private Map<String, CategoryComparisonItem> buildCategoryComparison(
            Long sessionId,
            Long userNum,
            LocalDateTime createdAt,
            InterviewEvaluation currentEval,
            JsonNode evaluationJson
    ) {
        Map<String, Integer> userScores = resolveCategoryScores(currentEval, evaluationJson);
        Map<String, Integer> prevScores = parseCategoryScores(
                evaluationRepository.findPrevResultJson(userNum, createdAt)
        );

        Map<String, CategoryComparisonItem> result = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : userScores.entrySet()) {
            String category = entry.getKey();
            String jsonPath = categoryToJsonPath(category);

            Integer average = 0;
            if (jsonPath != null) {
                Double avgValue = evaluationRepository.avgFromResultJson(sessionId, jsonPath);
                average = avgValue == null ? 0 : clamp((int) Math.round(avgValue));
            }

            result.put(category, CategoryComparisonItem.builder()
                    .user(clamp(entry.getValue()))
                    .average(average)
                    .previous(clamp(prevScores.get(category)))
                    .build());
        }

        return result;
    }

    private Map<String, Integer> resolveCategoryScores(InterviewEvaluation currentEval, JsonNode evaluationJson) {
        Map<String, Integer> result = parseCategoryScores(currentEval.getResultJson());
        if (!result.isEmpty()) {
            return result;
        }

        if (evaluationJson != null) {
            result = extractCategoryScoresFromNode(evaluationJson);
            if (!result.isEmpty()) {
                return result;
            }
        }

        return new LinkedHashMap<>();
    }

    private void putIfNumber(Map<String, Integer> map, String key, JsonNode node) {
        if (node != null && node.isNumber()) {
            map.put(key, clamp(node.asInt()));
        }
    }

    private Map<String, Integer> parseCategoryScores(String json) {
        Map<String, Integer> result = new LinkedHashMap<>();

        if (json == null || json.isBlank()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            return extractCategoryScoresFromNode(root);
        } catch (Exception e) {
            return result;
        }
    }

    private Map<String, Integer> extractCategoryScoresFromNode(JsonNode root) {
        Map<String, Integer> result = new LinkedHashMap<>();

        if (root == null || root.isMissingNode() || root.isNull()) {
            return result;
        }

        JsonNode summary = root.path("summary");

        putIfNumber(result, "technical", summary.get("technicalIndex"));
        putIfNumber(result, "communication", summary.get("communicationIndex"));
        putIfNumber(result, "confidence", summary.get("confidenceIndex"));

        return result;
    }

    private String categoryToJsonPath(String category) {
        return switch (category) {
            case "technical" -> "$.summary.technicalIndex";
            case "communication" -> "$.summary.communicationIndex";
            case "confidence" -> "$.summary.confidenceIndex";
            default -> null;
        };
    }

    private Integer clamp(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }
}