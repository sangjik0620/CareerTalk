package com.careertalk.interview.service;

import com.careertalk.interview.dto.InterviewResultResponse;
import com.careertalk.interview.dto.InterviewResultV2Response;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InterviewResultService {

    private final InterviewService interviewService;                 // 기존 getVoiceResult()
    private final InterviewEvaluationService evaluationService;      // 분석 상태/결과 JSON
    private final InterviewTurnRepository turnRepository;            // turns DB 조회
    private final ObjectMapper objectMapper;


    public InterviewResultResponse getFullResult(Long sessionId) {
        InterviewSessionResultResponse voice = interviewService.getVoiceResult(sessionId);
        JsonNode evaluation = evaluationService.getOrCreateEvaluationResultJson(sessionId);
        return new InterviewResultResponse(voice, evaluation);
    }

    public InterviewResultV2Response getFullResultV2(Long sessionId) {

        // 1) 분석 상태
        String analysisStatus = evaluationService.getAnalysisStatus(sessionId);

        // 2) evaluation JSON (DONE일 때 존재할 수도, 없을 수도 있음)
        JsonNode evaluation = evaluationService.getEvaluationResultJsonOrNull(sessionId);

        // 3) 오디오 URL 포함 결과(이미 presigned url 생성 로직이 여기에 있을 가능성 큼)
        InterviewSessionResultResponse voice = interviewService.getVoiceResult(sessionId);

        // 4) DB turns (stt/scores/metrics 등은 여기)
        List<InterviewTurn> turnEntities = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // 5) voice.turns 를 turnNo 기준 맵으로 만들어서 audioUrl 매칭
        Map<Integer, InterviewSessionResultResponse.TurnItem> voiceTurnMap = new HashMap<>();
        if (voice != null && voice.turns() != null) {
            for (InterviewSessionResultResponse.TurnItem ti : voice.turns()) {
                voiceTurnMap.put(ti.turnNo(), ti);
            }
        }

        // 6) turn별 detail 구성
        List<InterviewResultV2Response.TurnDetail> details = new ArrayList<>();

        for (InterviewTurn t : turnEntities) {
            InterviewSessionResultResponse.TurnItem voiceItem = voiceTurnMap.get(t.getTurnNo());

            String audioUrl = (voiceItem != null) ? voiceItem.audioUrl() : null;

            Map<String, Object> audioMetrics = parseJsonToMap(t.getAudioMetricsJson());
            Map<String, Object> pythonExtracted = extractPythonExtracted(t.getPythonMetricsJson());

            InterviewResultV2Response.Scores scores = extractScoresSummary(t.getAudioScoresJson());

            InterviewResultV2Response.Audio audio = new InterviewResultV2Response.Audio(
                    t.getAnswerAudioFileId(),
                    audioUrl,
                    t.getAnswerAudioDurationSec()
            );

            InterviewResultV2Response.Metrics metrics = new InterviewResultV2Response.Metrics(
                    audioMetrics,
                    pythonExtracted
            );

            InterviewResultV2Response.TurnDetail dto = new InterviewResultV2Response.TurnDetail(
                    t.getTurnId(),
                    t.getTurnNo(),
                    t.getAiQuestion(),
                    (t.getSttStatus() != null ? t.getSttStatus().name() : null),
                    t.getSttText(),
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
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractPythonExtracted(String pythonMetricsJson) {
        if (pythonMetricsJson == null || pythonMetricsJson.isBlank()) return null;

        try {
            JsonNode root = objectMapper.readTree(pythonMetricsJson);

            JsonNode extracted = root.get("extracted");
            if (extracted != null && extracted.isObject()) {
                return objectMapper.convertValue(extracted, new TypeReference<Map<String, Object>>() {});
            }

            // fallback: flat 구조면 root 자체
            if (root != null && root.isObject()) {
                return objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private InterviewResultV2Response.Scores extractScoresSummary(String audioScoresJson) {

        if (audioScoresJson == null || audioScoresJson.isBlank()) {
            return new InterviewResultV2Response.Scores(
                    null, null, null, null, null, null, null, null
            );
        }

        try {
            JsonNode root = objectMapper.readTree(audioScoresJson);

            Integer tremorRiskScore = getIntPath(root, "tremor.tremorRiskScore");
            Integer confidenceScore = getIntPath(root, "confidence.confidenceScore");
            Integer fluencyScore    = getIntPath(root, "fluency.fluencyScore");
            Integer overallScore    = getIntPath(root, "overall.overallVoiceScore");
            String overallGrade     = getTextPath(root, "overall.grade");
            Double overallRel       = getDoublePath(root, "overall.overallReliability");

            // flags = tremor.flags[].code
            List<String> flags = new ArrayList<>();
            JsonNode flagsNode = getPath(root, "tremor.flags");
            if (flagsNode != null && flagsNode.isArray()) {
                for (JsonNode f : flagsNode) {
                    String code = (f != null && f.get("code") != null) ? f.get("code").asText() : null;
                    if (code != null) flags.add(code);
                }
            }

            Map<String, Object> raw = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});

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
            return new InterviewResultV2Response.Scores(
                    null, null, null, null, null, null, null, null
            );
        }
    }

    private JsonNode getPath(JsonNode root, String path) {
        if (root == null || root.isNull() || path == null || path.isBlank()) return null;
        JsonNode cur = root;
        for (String p : path.split("\\.")) {
            if (cur == null) return null;
            cur = cur.get(p);
        }
        return cur;
    }

    private Integer getIntPath(JsonNode root, String path) {
        JsonNode n = getPath(root, path);
        if (n == null || n.isNull()) return null;
        return n.asInt();
    }

    private Double getDoublePath(JsonNode root, String path) {
        JsonNode n = getPath(root, path);
        if (n == null || n.isNull()) return null;
        return n.asDouble();
    }

    private String getTextPath(JsonNode root, String path) {
        JsonNode n = getPath(root, path);
        if (n == null || n.isNull()) return null;
        return n.asText();
    }
}