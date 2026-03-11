package com.careertalk.interview.service;

import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewEvaluationRepository;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewOpenAiService interviewOpenAiService;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/interview_evaluation.txt")
    private Resource interviewEvalSystemPrompt;

    /**
     * 세션 종합 분석
     * - LLM은 1회만 호출
     * - LLM 응답의 questionResponses는 interview_turn.feedback_json 으로 분리 저장
     * - result_json 에는 세션 종합 정보만 저장
     */
    public void runAnalysisInternal(Long sessionId) throws Exception {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // 1) 서버 기본 구조 생성 (세션 종합 결과만)
        ObjectNode evaluation = buildEvaluationSkeleton(session, turns);

        // 2) LLM 결과 merge + turn.feedback_json 저장
        applyLlmInsights(evaluation, session, turns);

        // 3) 총점 계산: 이제 turn.feedback_json 의 score 평균으로 계산
        ObjectNode summary = (ObjectNode) evaluation.with("summary");
        ObjectNode comparison = (ObjectNode) evaluation.with("comparison");

        int overall = computeOverallScore(turns, turns.size());
        int percentileRank = calculatePercentile(overall, session.getSessionId());

        summary.put("overallScore", overall);
        summary.put("percentileRank", percentileRank);
        comparison.put("percentileRank", percentileRank);

        // 4) 세션 result_json 저장
        saveEvaluationResult(sessionId, overall, evaluation);
    }

    @Transactional
    public void saveEvaluationResult(Long sessionId, int overall, ObjectNode evaluation) {
        ObjectNode summary = (ObjectNode) evaluation.with("summary");

        String jsonStr = safeWrite(evaluation);
        String strengths = joinArray(summary.path("strengths"));
        String weaknesses = joinArray(summary.path("weaknesses"));
        String nextActions = joinNextActions(summary.path("nextActions"));

        evaluationRepository.upsert(sessionId, overall, strengths, weaknesses, nextActions, jsonStr);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(Long sessionId) {
        evaluationRepository.updateAnalysisStatus(sessionId, "DONE", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long sessionId, String message) {
        evaluationRepository.updateAnalysisStatus(sessionId, "FAILED", message);
    }

    @Transactional(readOnly = true)
    public JsonNode getEvaluationResultJsonOrNull(Long sessionId) {
        String saved = evaluationRepository.findResultJsonBySessionId(sessionId);
        if (saved == null || saved.isBlank()) return null;
        try {
            return objectMapper.readTree(saved);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String getAnalysisStatus(Long sessionId) {
        String status = evaluationRepository.findAnalysisStatusBySessionId(sessionId);
        if (status == null || status.isBlank()) {
//            System.out.println("[STATUS] no evaluation row for sessionId=" + sessionId);
            return "PENDING";
        }
//        System.out.println("[STATUS] sessionId=" + sessionId + ", status=" + status);
        return status;
    }

    /**
     * result_json skeleton 생성
     * - questionResponses 는 더 이상 session result_json 에 넣지 않음
     */
    private ObjectNode buildEvaluationSkeleton(InterviewSession session, List<InterviewTurn> turns) {
        ObjectNode root = objectMapper.createObjectNode();

        // interviewInfo
        ObjectNode info = root.putObject("interviewInfo");
        if (session.getCreatedAt() != null) {
            info.put("date", session.getCreatedAt().toLocalDate().toString());
        } else {
            info.put("date", "");
        }
        info.put("duration", formatDuration(session.getStartedAt(), session.getEndedAt()));

        String position = safe(session.getTitle()).trim();
        if (!position.isBlank()) {
            info.put("position", position);
        }

        int answered = 0;
        int total = turns.size();
        int totalWords = 0;
        int totalRespSec = 0;
        int respSecCount = 0;

        for (InterviewTurn t : turns) {
            String answer = safe(firstNonBlank(t.getUserAnswerText(), t.getSttText(), "")).trim();
            if (!answer.isBlank()) answered++;

            totalWords += countWords(answer);

            Integer dur = t.getAnswerAudioDurationSec();
            if (dur != null && dur > 0) {
                totalRespSec += dur;
                respSecCount++;
            }
        }

        int avgRespSec = respSecCount == 0 ? 0 : (int) Math.round(totalRespSec * 1.0 / respSecCount);

        // interviewAnalysis
        ObjectNode interview = root.putObject("interviewAnalysis");
        interview.set("voiceCoaching", objectMapper.createArrayNode());

        ObjectNode sttAnalysis = interview.putObject("sttAnalysis");
        sttAnalysis.put("totalWords", Math.max(0, totalWords));
        sttAnalysis.put("averageResponseTime", Math.max(0, avgRespSec));
        sttAnalysis.putNull("sentimentScore");

        ObjectNode keywordUsage = sttAnalysis.putObject("keywordUsage");
        keywordUsage.put("technical", 0);
        keywordUsage.put("soft", 0);
        keywordUsage.put("company", 0);

        sttAnalysis.put("overallFeedback", "");

        // summary
        ObjectNode summary = root.putObject("summary");
        summary.put("answeredQuestions", answered);
        summary.put("totalQuestions", total);
        summary.put("completionRate", total == 0 ? 0 : clamp0_100((int) Math.round(answered * 100.0 / total)));
        summary.put("avgResponseSec", Math.max(0, avgRespSec));
        summary.put("totalWordCount", Math.max(0, totalWords));
        summary.put("fillerWordCount", (Integer) null);

        summary.putNull("overallScore");
        summary.putNull("percentileRank");
        summary.putNull("previousScore");
        summary.putNull("passedAverage");
        summary.putNull("fillerWordRate");
        summary.putNull("sentimentScore");
        summary.putNull("jobFitIndex");
        summary.putNull("confidenceIndex");
        summary.putNull("technicalIndex");
        summary.putNull("communicationIndex");
        summary.putNull("verdict");

        summary.set("topKeywords", objectMapper.createArrayNode());
        summary.set("strengths", objectMapper.createArrayNode());
        summary.set("weaknesses", objectMapper.createArrayNode());
        summary.set("nextActions", objectMapper.createArrayNode());

        // competency
        ObjectNode competency = root.putObject("competency");
        competency.set("technical", emptyCompetencyBlock());
        competency.set("soft", emptyCompetencyBlock());
        competency.set("improvements", objectMapper.createArrayNode());

        // comparison
        ObjectNode comparison = root.putObject("comparison");
        comparison.putNull("percentileRank");
        comparison.set("scoreHistory", objectMapper.createArrayNode());
        comparison.set("categoryComparison", emptyCategoryComparison());


        return root;
    }

    /**
     * LLM 1회 호출
     * - summary / competency / voiceCoaching 는 session result_json 에 merge
     * - questionResponses 는 turn.feedback_json 으로 저장
     */
    private void applyLlmInsights(ObjectNode evaluation, InterviewSession session, List<InterviewTurn> turns) {
        ObjectNode meta = evaluation.with("meta");
        meta.put("llmProvider", "openai");
        meta.put("llmModel", safe(modelNameOrUnknown()));
        meta.put("llmStatus", "PENDING");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sessionId", session.getSessionId());
        payload.put("position", safe(session.getTitle()));
        payload.put("createdAt", session.getCreatedAt() == null ? "" : session.getCreatedAt().toString());

        ArrayNode tArr = payload.putArray("turns");
        for (InterviewTurn t : turns) {
            ObjectNode one = tArr.addObject();
            one.put("turnNo", safeInt(t.getTurnNo(), 0));
            one.put("question", safe(t.getAiQuestion()));
            one.put("answer", safe(firstNonBlank(t.getUserAnswerText(), t.getSttText(), "")).trim());
            one.put("durationSec", safeInt(t.getAnswerAudioDurationSec(), 0));

            JsonNode scoreRoot = parseJson(t.getAudioScoresJson());
            ObjectNode voice = one.putObject("voice");
            voice.put("overallVoiceScore", nvl(firstInt(
                    scoreRoot,
                    "overall.overallVoiceScore",
                    "overallVoiceScore",
                    "voice.overallVoiceScore"
            )));
            voice.put("confidenceScore", nvl(firstInt(
                    scoreRoot,
                    "confidence.confidenceScore",
                    "confidenceScore",
                    "voice.confidenceScore"
            )));
            voice.put("fluencyScore", nvl(firstInt(
                    scoreRoot,
                    "fluency.fluencyScore",
                    "fluencyScore",
                    "voice.fluencyScore"
            )));
            voice.put("tremorRiskScore", nvl(firstInt(
                    scoreRoot,
                    "tremor.tremorRiskScore",
                    "tremorRiskScore",
                    "voice.tremorRiskScore"
            )));
        }

        String system = readResource(interviewEvalSystemPrompt);
        String user = "면접 원자료:\n" + safeWrite(payload);

        JsonNode out = interviewOpenAiService.callJsonOnly(system, user);

        if (out == null || out.isNull()) {
            meta.put("llmStatus", "FAILED");
            throw new IllegalStateException("LLM output is null");
        }

        log.info("[LLM SUMMARY RAW] {}", safeWrite(out.path("summary")));
        log.info("[LLM RAW OUT] summaryExists={}, competencyExists={}, interviewExists={}, qResponsesSize={}",
                out.path("summary").isObject(),
                out.path("competency").isObject(),
                out.path("interviewAnalysis").isObject(),
                out.path("interviewAnalysis").path("questionResponses").size());

        if (out.has("error")) {
            meta.put("llmStatus", "FAILED");
            String raw = out.path("raw").asText("");
            throw new IllegalStateException("LLM returned invalid JSON. raw=" + shorten(raw, 500));
        }

        // summary merge
        ObjectNode summary = (ObjectNode) evaluation.with("summary");
        JsonNode outSummary = out.path("summary");

        summary.set("topKeywords", normalizeTopKeywords(outSummary.path("topKeywords")));
        summary.set("strengths", arrayOrEmpty(outSummary.path("strengths")));
        summary.set("weaknesses", arrayOrEmpty(outSummary.path("weaknesses")));
        summary.set("nextActions", normalizeNextActions(outSummary.path("nextActions")));

        summary.put("jobFitIndex", clamp0_100(outSummary.path("jobFitIndex").asInt(0)));
        summary.put("confidenceIndex", clamp0_100(outSummary.path("confidenceIndex").asInt(0)));
        summary.put("technicalIndex", clamp0_100(outSummary.path("technicalIndex").asInt(0)));
        summary.put("communicationIndex", clamp0_100(outSummary.path("communicationIndex").asInt(0)));
        summary.put("sentimentScore", clamp0_100(outSummary.path("sentimentScore").asInt(0)));

        int fillerWordCount = Math.max(0, outSummary.path("fillerWordCount").asInt(0));
        summary.put("fillerWordCount", fillerWordCount);

        int totalWordCount = Math.max(0, summary.path("totalWordCount").asInt(0));
        int fillerWordRate = 0;
        if (totalWordCount > 0) {
            fillerWordRate = clamp0_100((int) Math.round((fillerWordCount * 100.0) / totalWordCount));
        }
        summary.put("fillerWordRate", fillerWordRate);

        if (!outSummary.path("verdict").asText("").trim().isBlank()) {
            summary.put("verdict", outSummary.path("verdict").asText("").trim());
        }

        // competency merge
        ObjectNode competency = (ObjectNode) evaluation.with("competency");
        JsonNode outComp = out.path("competency");

        JsonNode technical = outComp.path("technical");
        if (technical.isObject()) {
            competency.set("technical", technical.deepCopy());
        }

        JsonNode soft = outComp.path("soft");
        if (soft.isObject()) {
            competency.set("soft", soft.deepCopy());
        }

        competency.set("improvements", normalizeImprovements(outComp.path("improvements")));

        // summary fallback
        int technicalIndex = clamp0_100(summary.path("technicalIndex").asInt(0));
        if (technicalIndex == 0 && competency.path("technical").path("current").isNumber()) {
            technicalIndex = clamp0_100(competency.path("technical").path("current").asInt());
        }
        summary.put("technicalIndex", technicalIndex);

        int communicationIndex = clamp0_100(summary.path("communicationIndex").asInt(0));
        if (communicationIndex == 0 && competency.path("soft").path("details").path("communication").isNumber()) {
            communicationIndex = clamp0_100(competency.path("soft").path("details").path("communication").asInt());
        }
        summary.put("communicationIndex", communicationIndex);

        int confidenceIndex = clamp0_100(summary.path("confidenceIndex").asInt(0));
        if (confidenceIndex == 0) {
            int sum = 0;
            int count = 0;
            for (InterviewTurn t : turns) {
                JsonNode scoreRoot = parseJson(t.getAudioScoresJson());
                Integer c = firstInt(
                        scoreRoot,
                        "confidence.confidenceScore",
                        "confidenceScore",
                        "voice.confidenceScore"
                );
                if (c != null) {
                    sum += c;
                    count++;
                }
            }
            if (count > 0) {
                confidenceIndex = clamp0_100((int) Math.round(sum * 1.0 / count));
            }
        }
        summary.put("confidenceIndex", confidenceIndex);

        // interviewAnalysis merge
        ObjectNode interview = (ObjectNode) evaluation.with("interviewAnalysis");
        JsonNode outInterview = out.path("interviewAnalysis");

        interview.set("voiceCoaching", arrayOrEmpty(outInterview.path("voiceCoaching")));

        ObjectNode sttAnalysis = (ObjectNode) interview.with("sttAnalysis");
        JsonNode outStt = outInterview.path("sttAnalysis");

        String overallFeedback = outStt.path("overallFeedback").asText("").trim();
        sttAnalysis.put("overallFeedback", overallFeedback);

        JsonNode keywordUsageNode = outStt.path("keywordUsage");
        if (keywordUsageNode.isObject()) {
            ObjectNode keywordUsage = (ObjectNode) sttAnalysis.with("keywordUsage");
            keywordUsage.put("technical", Math.max(0, keywordUsageNode.path("technical").asInt(0)));
            keywordUsage.put("soft", Math.max(0, keywordUsageNode.path("soft").asInt(0)));
            keywordUsage.put("company", Math.max(0, keywordUsageNode.path("company").asInt(0)));
        }

        sttAnalysis.put("sentimentScore", clamp0_100(outSummary.path("sentimentScore").asInt(0)));

        // questionResponses 는 turn.feedback_json 으로 저장
        saveQuestionResponsesToTurns(turns, outInterview.path("questionResponses"));

        meta.put("llmStatus", "OK");
        meta.put("llmMergedAt", LocalDateTime.now().toString());
    }

    private int calculatePercentile(int overallScore, Long sessionId) {

        List<Object[]> rows = evaluationRepository.getScoreStats(overallScore, sessionId);

        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        Object[] stats = rows.get(0);

        long lowerCount = stats[0] == null ? 0L : ((Number) stats[0]).longValue();
        long equalCount = stats[1] == null ? 0L : ((Number) stats[1]).longValue();
        long totalCount = stats[2] == null ? 0L : ((Number) stats[2]).longValue();

        if (totalCount <= 0) {
            return 0;
        }

        double percentile =
                ((lowerCount + (equalCount * 0.5)) * 100.0) / totalCount;

        return clamp0_100((int) Math.round(percentile));
    }

    /**
     * LLM questionResponses -> interview_turn.feedback_json 저장
     * feedback_json 은 기존값 merge 없이 turn별로 새로 생성하여 저장한다.
     */
    private void saveQuestionResponsesToTurns(List<InterviewTurn> turns, JsonNode outArr) {
        if (outArr == null || !outArr.isArray()) {
            log.warn("[LLM MERGE] questionResponses is empty or not array");
            return;
        }

        Map<Integer, InterviewTurn> turnMap = new HashMap<>();
        for (InterviewTurn t : turns) {
            turnMap.put(safeInt(t.getTurnNo(), 0), t);
        }

        int savedCount = 0;

        for (JsonNode node : outArr) {
            int turnNo = node.path("turnNo").asInt(0);
            if (turnNo <= 0) continue;

            InterviewTurn turn = turnMap.get(turnNo);
            if (turn == null) continue;

            ObjectNode feedback = objectMapper.createObjectNode();

            feedback.put("score", clamp0_100(node.path("score").asInt(0)));
            feedback.put("oneLineFeedback", node.path("oneLineFeedback").asText("").trim());
            feedback.put("fullFeedback", node.path("fullFeedback").asText("").trim());

            JsonNode sentimentNode = node.get("sentimentScore");
            if (sentimentNode != null && !sentimentNode.isNull() && sentimentNode.isNumber()) {
                feedback.put("sentimentScore", clamp0_100(sentimentNode.asInt()));
            } else {
                feedback.putNull("sentimentScore");
            }

            JsonNode keywordsNode = node.get("keywords");
            if (keywordsNode != null && keywordsNode.isArray()) {
                feedback.set("keywords", keywordsNode.deepCopy());
            } else {
                feedback.set("keywords", objectMapper.createArrayNode());
            }

            turn.setFeedbackJson(safeWrite(feedback));
            savedCount++;
        }

        if (savedCount > 0) {
            turnRepository.saveAll(turns);
        }

        log.info("[LLM MERGE] saved turn feedback_json={}/{}", savedCount, turns.size());
    }

    private ArrayNode normalizeTopKeywords(JsonNode node) {
        ArrayNode out = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) return out;

        for (JsonNode it : node) {
            String s = it.asText("").trim();
            if (s.isBlank()) continue;
            out.add(s);
            if (out.size() >= 3) break;
        }
        return out;
    }

    private String modelNameOrUnknown() {
        return "unknown";
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private String formatDuration(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null) return "";
        try {
            long sec = Math.max(0, Duration.between(startedAt, endedAt).getSeconds());
            long mm = sec / 60;
            long ss = sec % 60;
            return String.format("%d:%02d", mm, ss);
        } catch (Exception e) {
            return "";
        }
    }

    private String safeWrite(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ArrayNode arrayOrEmpty(JsonNode node) {
        if (node != null && node.isArray()) return (ArrayNode) node;
        return objectMapper.createArrayNode();
    }

    private ArrayNode normalizeNextActions(JsonNode node) {
        ArrayNode out = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) return out;

        for (JsonNode it : node) {
            String title = it.path("title").asText("").trim();
            int dueDays = it.path("dueDays").asInt(0);
            if (title.isBlank()) continue;

            ObjectNode one = objectMapper.createObjectNode();
            one.put("title", title);
            one.put("dueDays", Math.max(0, dueDays));
            out.add(one);
        }
        return out;
    }

    private ArrayNode normalizeImprovements(JsonNode node) {
        ArrayNode out = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) return out;

        for (JsonNode it : node) {
            String area = it.path("area").asText("").trim();
            String priority = it.path("priority").asText("medium").trim();
            int current = clamp0_100(it.path("currentLevel").asInt(0));
            int target = clamp0_100(it.path("targetLevel").asInt(0));
            if (area.isBlank()) continue;

            if (!priority.equals("high") && !priority.equals("medium") && !priority.equals("low")) {
                priority = "medium";
            }

            ObjectNode one = objectMapper.createObjectNode();
            one.put("area", area);
            one.put("priority", priority);
            one.put("currentLevel", current);
            one.put("targetLevel", target);

            ArrayNode items = objectMapper.createArrayNode();
            JsonNode ai = it.path("actionItems");
            if (ai.isArray()) {
                for (JsonNode s : ai) {
                    String v = s.asText("").trim();
                    if (!v.isBlank()) items.add(v);
                }
            }
            one.set("actionItems", items);
            out.add(one);
        }
        return out;
    }

    private int clamp0_100(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : arr) {
            String s = n.asText("").trim();
            if (s.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(s);
        }
        return sb.toString();
    }

    private String joinNextActions(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : arr) {
            String title = n.path("title").asText("").trim();
            if (title.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(title);
        }
        return sb.toString();
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private int safeInt(Integer v, int def) {
        return v == null ? def : v;
    }

    private String firstNonBlank(String a, String b, String def) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return def;
    }

    private int countWords(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    private String readResource(Resource r) {
        try {
            return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read prompt resource", e);
        }
    }

    /**
     * 총점은 세션 result_json.questionResponses 가 아니라
     * interview_turn.feedback_json.score 평균으로 계산
     */
    private int computeOverallScore(List<InterviewTurn> turns, int totalQuestions) {
        double contentScore = computeContentScore(turns);
        double voiceScore = computeVoiceScore(turns);
        double completionScore = computeCompletionScore(turns, totalQuestions);

        int overall = (int) Math.round(
                contentScore * 0.60 +
                        voiceScore * 0.25 +
                        completionScore * 0.15
        );

        return clamp0_100(overall);
    }

    private int computeContentScore(List<InterviewTurn> turns) {
        int sum = 0;
        int count = 0;

        for (InterviewTurn turn : turns) {
            try {
                String json = turn.getFeedbackJson();
                if (json == null || json.isBlank()) continue;

                JsonNode node = objectMapper.readTree(json);
                int score = clamp0_100(node.path("score").asInt(0));

                sum += score;
                count++;
            } catch (Exception ignored) {
            }
        }

        if (count == 0) return 0;
        return clamp0_100((int) Math.round(sum * 1.0 / count));
    }

    private int computeVoiceScore(List<InterviewTurn> turns) {
        int sum = 0;
        int count = 0;

        for (InterviewTurn turn : turns) {
            try {
                String json = turn.getAudioScoresJson();
                if (json == null || json.isBlank()) continue;

                JsonNode node = objectMapper.readTree(json);

                Integer score = firstInt(
                        node,
                        "overall.overallVoiceScore",
                        "overallVoiceScore",
                        "voice.overallVoiceScore"
                );

                if (score != null) {
                    sum += clamp0_100(score);
                    count++;
                }
            } catch (Exception ignored) {
            }
        }

        if (count == 0) return 0;
        return clamp0_100((int) Math.round(sum * 1.0 / count));
    }

    private int computeCompletionScore(List<InterviewTurn> turns, int totalQuestions) {
        if (totalQuestions <= 0) return 0;

        int answered = 0;

        for (InterviewTurn turn : turns) {
            String answer = firstNonBlank(
                    turn.getUserAnswerText(),
                    turn.getSttText(),
                    ""
            ).trim();

            if (!answer.isBlank()) {
                answered++;
            }
        }

        return clamp0_100((int) Math.round(answered * 100.0 / totalQuestions));
    }

    private ObjectNode emptyCompetencyBlock() {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("current", 0);
        block.put("target", 0);
        block.set("details", objectMapper.createObjectNode());
        return block;
    }

    private ObjectNode emptyCategoryComparison() {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("technical", emptyCategoryScoreBlock());
        root.set("communication", emptyCategoryScoreBlock());
        root.set("confidence", emptyCategoryScoreBlock());
        return root;
    }

    private ObjectNode emptyCategoryScoreBlock() {
        ObjectNode block = objectMapper.createObjectNode();
        block.putNull("myScore");
        block.putNull("averageScore");
        block.putNull("previous");
        return block;
    }

    private JsonNode parseJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Integer firstInt(JsonNode root, String... paths) {
        if (root == null || root.isMissingNode() || root.isNull()) return null;

        for (String path : paths) {
            JsonNode cur = root;
            boolean ok = true;

            for (String part : path.split("\\.")) {
                cur = cur.path(part);
                if (cur.isMissingNode() || cur.isNull()) {
                    ok = false;
                    break;
                }
            }

            if (ok && cur.isNumber()) {
                return cur.asInt();
            }
        }
        return null;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}