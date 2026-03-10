package com.careertalk.interview.service;

import com.careertalk.interview.dto.ComparisonResponse;
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
    private final InterviewComparisonService comparisonService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InterviewResultV2Response getFullResultV2(Long sessionId) {

        String analysisStatus = evaluationService.getAnalysisStatus(sessionId);
        JsonNode evaluation = evaluationService.getEvaluationResultJsonOrNull(sessionId);

        JsonNode sanitizedEvaluation = sanitizeEvaluation(evaluation);

        ComparisonResponse comparison = comparisonService.buildComparison(sessionId, sanitizedEvaluation);

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
            InterviewSessionResultResponse.TurnItem voiceItem = voiceTurnMap.get(t.getTurnNo());
            String audioUrl = (voiceItem != null) ? voiceItem.audioUrl() : null;

            Map<String, Object> audioMetrics = parseJsonToMap(t.getAudioMetricsJson());
            Map<String, Object> pythonExtracted = parseJsonToMap(t.getPythonMetricsJson());

            InterviewResultV2Response.Scores scores = extractScoresSummary(t.getAudioScoresJson());
            InterviewResultV2Response.Feedback feedback = extractFeedbackSummary(t.getFeedbackJson());

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
                            scores,
                            feedback
                    );

            details.add(dto);
        }

        return new InterviewResultV2Response(
                sessionId,
                analysisStatus,
                LocalDateTime.now(),
                sanitizedEvaluation,
                comparison,
                details
        );
    }

    private JsonNode sanitizeEvaluation(JsonNode evaluation) {
        if (evaluation == null || !evaluation.isObject()) {
            return evaluation;
        }

        JsonNode copy = evaluation.deepCopy();

        if (copy instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            obj.remove("comparison");
        }

        return copy;
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

    private InterviewResultV2Response.Feedback extractFeedbackSummary(String json) {
        if (json == null || json.isBlank()) {
            return emptyFeedback();
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            Integer score = root.path("score").isNumber() ? root.path("score").asInt() : null;

            String oneLineFeedback = blankToNull(root.path("oneLineFeedback").asText(null));
            String fullFeedback = blankToNull(root.path("fullFeedback").asText(null));
            Integer sentimentScore = root.path("sentimentScore").isNumber() ? root.path("sentimentScore").asInt() : null;

            JsonNode kw = root.path("keywords");
            InterviewResultV2Response.Keywords keywords = new InterviewResultV2Response.Keywords(
                    toStringList(kw.path("technical")),
                    toStringList(kw.path("soft")),
                    toStringList(kw.path("company"))
            );

            JsonNode voice = root.path("voice");
            InterviewResultV2Response.VoiceFeedback voiceFeedback = new InterviewResultV2Response.VoiceFeedback(
                    getNullableInt(voice, "overallVoiceScore"),
                    getNullableInt(voice, "confidenceScore"),
                    getNullableInt(voice, "fluencyScore"),
                    getNullableInt(voice, "tremorRiskScore"),
                    toStringList(voice.path("strengths")),
                    toStringList(voice.path("weaknesses"))
            );

            return new InterviewResultV2Response.Feedback(
                    score,
                    oneLineFeedback,
                    fullFeedback,
                    sentimentScore,
                    keywords,
                    voiceFeedback
            );

        } catch (Exception e) {
            return emptyFeedback();
        }
    }

    private InterviewResultV2Response.Feedback emptyFeedback() {
        return new InterviewResultV2Response.Feedback(
                null,
                null,
                null,
                null,
                new InterviewResultV2Response.Keywords(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                new InterviewResultV2Response.VoiceFeedback(
                        null,
                        null,
                        null,
                        null,
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );
    }

    private InterviewResultV2Response.Scores extractScoresSummary(String json) {

        if (json == null || json.isBlank()) {
            return emptyScores();
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            Integer tremorRiskScore = firstInt(
                    root,
                    "tremor.tremorRiskScore",
                    "tremorRiskScore"
            );

            Integer confidenceScore = firstInt(
                    root,
                    "confidence.confidenceScore",
                    "confidenceScore"
            );

            Integer fluencyScore = firstInt(
                    root,
                    "fluency.fluencyScore",
                    "fluencyScore"
            );

            Integer overallScore = firstInt(
                    root,
                    "overall.overallVoiceScore",
                    "overallVoiceScore"
            );

            String overallGrade = firstText(
                    root,
                    "overall.grade",
                    "overallGrade"
            );

            Double overallRel = firstDouble(
                    root,
                    "overall.overallReliability",
                    "overallReliability"
            );

            if (overallRel == null) {
                Double confRel = firstDouble(root, "confidence.analysisReliability");
                Double tremorRel = firstDouble(root, "tremor.analysisReliability");

                if (confRel != null && tremorRel != null) {
                    overallRel = (confRel + tremorRel) / 2.0;
                } else if (confRel != null) {
                    overallRel = confRel;
                } else if (tremorRel != null) {
                    overallRel = tremorRel;
                }
            }

            List<String> flags = extractFlags(root);

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
                null, null, null, null, null, null, new ArrayList<>(), null
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

    private Integer firstInt(JsonNode root, String... paths) {
        for (String path : paths) {
            Integer value = getIntPath(root, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode root, String... paths) {
        for (String path : paths) {
            Double value = getDoublePath(root, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... paths) {
        for (String path : paths) {
            String value = getTextPath(root, path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<String> extractFlags(JsonNode root) {
        List<String> out = new ArrayList<>();

        collectFlagCodes(out, getPath(root, "tremor.flags"));
        collectFlagCodes(out, getPath(root, "confidence.flags"));
        collectFlagCodes(out, getPath(root, "flags"));

        return out.stream().distinct().toList();
    }

    private void collectFlagCodes(List<String> out, JsonNode flagsNode) {
        if (flagsNode == null || !flagsNode.isArray()) {
            return;
        }

        for (JsonNode f : flagsNode) {
            if (f == null) {
                continue;
            }

            if (f.isTextual()) {
                String code = f.asText();
                if (!code.isBlank()) {
                    out.add(code);
                }
                continue;
            }

            JsonNode codeNode = f.get("code");
            if (codeNode != null) {
                String code = codeNode.asText();
                if (code != null && !code.isBlank()) {
                    out.add(code);
                }
            }
        }
    }

    private Integer getNullableInt(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || !n.isNumber()) {
            return null;
        }
        return n.asInt();
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String s = item.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}