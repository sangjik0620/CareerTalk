package com.careertalk.interview.service;

import com.careertalk.interview.dto.InterviewResultV2Response;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InterviewResultService {

    private final InterviewService interviewService;
    private final InterviewEvaluationService evaluationService;
    private final InterviewTurnRepository turnRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InterviewResultV2Response getFullResultV2(Long sessionId) {

        String analysisStatus = evaluationService.getAnalysisStatus(sessionId);
        JsonNode evaluation = evaluationService.getEvaluationResultJsonOrNull(sessionId);

        InterviewSessionResultResponse voice = interviewService.getVoiceResult(sessionId);

        List<InterviewTurn> turnEntities =
                turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        Map<Integer, InterviewSessionResultResponse.TurnItem> voiceTurnMap = new HashMap<>();

        if (voice != null && voice.turns() != null) {
            for (InterviewSessionResultResponse.TurnItem ti : voice.turns()) {
                voiceTurnMap.put(ti.turnNo(), ti);
            }
        }

        List<InterviewResultV2Response.TurnDetail> details = new ArrayList<>();

        for (InterviewTurn t : turnEntities) {

            InterviewSessionResultResponse.TurnItem voiceItem =
                    voiceTurnMap.get(t.getTurnNo());

            String audioUrl = (voiceItem != null) ? voiceItem.audioUrl() : null;

            Map<String, Object> audioMetrics =
                    parseJsonToMap(t.getAudioMetricsJson());

            Map<String, Object> pythonExtracted =
                    extractPythonExtracted(t.getPythonMetricsJson());

            InterviewResultV2Response.Scores scores =
                    extractScoresSummary(t.getAudioScoresJson());

            InterviewResultV2Response.Audio audio =
                    new InterviewResultV2Response.Audio(
                            t.getAnswerAudioFileId(),
                            audioUrl,
                            t.getAnswerAudioDurationSec()
                    );

            InterviewResultV2Response.Metrics metrics =
                    new InterviewResultV2Response.Metrics(
                            audioMetrics,
                            pythonExtracted
                    );

            InterviewResultV2Response.TurnDetail dto =
                    new InterviewResultV2Response.TurnDetail(
                            t.getTurnId(),
                            t.getTurnNo(),
                            t.getAiQuestion(),
                            (t.getSttStatus() != null ? t.getSttStatus().name() : null),
                            firstNonBlank(t.getUserAnswerText(), t.getSttText(), null),
                            audio,
                            metrics,
                            scores
                    );

            details.add(dto);
        }

        return new InterviewResultV2Response(
                sessionId,
                analysisStatus,
                LocalDateTime.now(),
                evaluation,
                details
        );
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return null;

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractPythonExtracted(String json) {

        if (json == null || json.isBlank()) return null;

        try {

            JsonNode root = objectMapper.readTree(json);

            JsonNode extracted = root.get("extracted");

            if (extracted != null && extracted.isObject()) {

                return objectMapper.convertValue(
                        extracted,
                        new TypeReference<Map<String, Object>>() {}
                );
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private InterviewResultV2Response.Scores extractScoresSummary(String json) {

        if (json == null || json.isBlank()) {
            return emptyScores();
        }

        try {

            JsonNode root = objectMapper.readTree(json);

            Integer tremorRiskScore =
                    getIntPath(root, "tremor.tremorRiskScore");

            Integer confidenceScore =
                    getIntPath(root, "confidence.confidenceScore");

            Integer fluencyScore =
                    getIntPath(root, "fluency.fluencyScore");

            Integer overallScore =
                    getIntPath(root, "overall.overallVoiceScore");

            String overallGrade =
                    getTextPath(root, "overall.grade");

            Double overallRel =
                    getDoublePath(root, "overall.overallReliability");

            List<String> flags = new ArrayList<>();

            JsonNode flagsNode = getPath(root, "tremor.flags");

            if (flagsNode != null && flagsNode.isArray()) {

                for (JsonNode f : flagsNode) {

                    String code =
                            (f != null && f.get("code") != null)
                                    ? f.get("code").asText()
                                    : null;

                    if (code != null && !code.isBlank()) {
                        flags.add(code);
                    }
                }
            }

            Map<String, Object> raw =
                    objectMapper.convertValue(
                            root,
                            new TypeReference<Map<String, Object>>() {}
                    );

            return new InterviewResultV2Response.Scores(
                    tremorRiskScore,
                    confidenceScore,
                    fluencyScore,
                    overallScore,
                    overallGrade,
                    overallRel,
                    flags,
                    raw
            );

        } catch (Exception e) {
            return emptyScores();
        }
    }

    private InterviewResultV2Response.Scores emptyScores() {
        return new InterviewResultV2Response.Scores(
                null, null, null, null, null, null, null, null
        );
    }

    private JsonNode getPath(JsonNode root, String path) {

        if (root == null) return null;

        JsonNode cur = root;

        for (String p : path.split("\\.")) {

            if (cur == null) return null;

            cur = cur.get(p);
        }

        return cur;
    }

    private Integer getIntPath(JsonNode root, String path) {

        JsonNode n = getPath(root, path);

        if (n == null || !n.isNumber()) return null;

        return n.asInt();
    }

    private Double getDoublePath(JsonNode root, String path) {

        JsonNode n = getPath(root, path);

        if (n == null || !n.isNumber()) return null;

        return n.asDouble();
    }

    private String getTextPath(JsonNode root, String path) {

        JsonNode n = getPath(root, path);

        if (n == null) return null;

        String s = n.asText(null);

        return (s == null || s.isBlank()) ? null : s;
    }

    private String firstNonBlank(String a, String b, String def) {

        if (a != null && !a.isBlank()) return a;

        if (b != null && !b.isBlank()) return b;

        return def;
    }
}