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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewOpenAiService interviewOpenAiService; // ✅ 인터뷰 전용 1회 호출 서비스
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/interview_evaluation.txt")
    private Resource interviewEvalSystemPrompt;

    /**
     * ✅ 분석 파이프라인 (유일한 생성 지점)
     * - 세션/턴 로드
     * - 서버는 "수치/구조"만 생성
     * - LLM 1회 호출로 "텍스트/액션/키워드/보이스 코칭" 생성
     * - DB upsert(result_json)
     */
    public void runAnalysisInternal(Long sessionId) throws Exception {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // 1) 서버 기본 구조 생성
        ObjectNode evaluation = buildEvaluationSkeleton(session, turns);

        // 2) LLM 결과 merge (트랜잭션 밖)
        applyLlmInsights(evaluation, session, turns);

        // 3) 총점 계산
        ObjectNode summary = (ObjectNode) evaluation.with("summary");
        JsonNode questionResponses = evaluation.path("interviewAnalysis").path("questionResponses");

        int overall = computeOverallFromQuestionResponses(questionResponses);
        summary.put("overallScore", overall);

        // 4) DB 저장만 별도 트랜잭션
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

    // ─────────────────────────────────────────────────────────────
    // Status helpers (Worker/Controller에서 사용)
    // ─────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long sessionId) {
        evaluationRepository.updateAnalysisStatus(sessionId, "PROCESSING", null);
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
            System.out.println("[STATUS] no evaluation row for sessionId=" + sessionId);
            return "PENDING";
        }
        System.out.println("[STATUS] sessionId=" + sessionId + ", status=" + status);
        return status;
    }

    // ─────────────────────────────────────────────────────────────
    // 1) 서버 계산: "구조/수치"만 만들고 텍스트는 비움 (LLM이 채움)
    // ─────────────────────────────────────────────────────────────

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

        // position: 현재 DB 컬럼 없어서 title로 유지(기존 코드 호환)
        String position = safe(session.getTitle()).trim();
        if (!position.isBlank()) info.put("position", position);

        // interviewAnalysis
        ObjectNode interview = root.putObject("interviewAnalysis");
        ArrayNode qArr = interview.putArray("questionResponses");

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

            ObjectNode one = qArr.addObject();
            one.put("turnNo", safeInt(t.getTurnNo(), 0));
            one.put("question", safe(t.getAiQuestion()));
            one.put("answer", answer);

            // LLM merge 전 기본값
            one.putNull("score");
            one.put("oneLineFeedback", "");
            one.put("fullFeedback", "");
        }

        // summary
        ObjectNode summary = root.putObject("summary");
        summary.put("answeredQuestions", answered);
        summary.put("totalQuestions", total);
        summary.put("completionRate", total == 0 ? 0 : clamp0_100((int) Math.round(answered * 100.0 / total)));

        int avgRespSec = respSecCount == 0 ? 0 : (int) Math.round(totalRespSec * 1.0 / respSecCount);
        summary.put("avgResponseSec", Math.max(0, avgRespSec));
        summary.put("totalWordCount", Math.max(0, totalWords));

        // 현재 세션만으로 계산 안 되거나 아직 산식이 없는 값은 null
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

        // LLM 채움 대상
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

        // interviewAnalysis.voiceCoaching
        interview.set("voiceCoaching", objectMapper.createArrayNode());

        return root;
    }

    // ─────────────────────────────────────────────────────────────
    // 2) LLM 1회 호출 + merge (서버 템플릿/폴백 금지)
    // ─────────────────────────────────────────────────────────────

    private void applyLlmInsights(ObjectNode evaluation, InterviewSession session, List<InterviewTurn> turns) {
        ObjectNode meta = evaluation.with("meta");
        meta.put("llmProvider", "openai");
        meta.put("llmModel", safe(modelNameOrUnknown()));
        meta.put("llmStatus", "PENDING");

        // 1) LLM 입력 payload
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
        }

        String system = readResource(interviewEvalSystemPrompt);
        String user = "면접 원자료:\n" + safeWrite(payload);

        JsonNode out = interviewOpenAiService.callJsonOnly(system, user);

        if (out == null || out.isNull()) {
            meta.put("llmStatus", "FAILED");
            throw new IllegalStateException("LLM output is null");
        }

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

        // 2) summary merge
        ObjectNode summary = (ObjectNode) evaluation.with("summary");
        JsonNode outSummary = out.path("summary");
        summary.set("topKeywords", normalizeTopKeywords(outSummary.path("topKeywords")));
        summary.set("strengths", arrayOrEmpty(outSummary.path("strengths")));
        summary.set("weaknesses", arrayOrEmpty(outSummary.path("weaknesses")));
        summary.set("nextActions", normalizeNextActions(outSummary.path("nextActions")));

        // 3) competency merge
        ObjectNode competency = (ObjectNode) evaluation.with("competency");
        JsonNode outComp = out.path("competency");

        JsonNode technical = outComp.path("technical");
        if (technical.isObject()) {
            competency.set("technical", (ObjectNode) technical);
        }

        JsonNode soft = outComp.path("soft");
        if (soft.isObject()) {
            competency.set("soft", (ObjectNode) soft);
        }

        competency.set("improvements", normalizeImprovements(outComp.path("improvements")));

        // 4) interviewAnalysis merge
        ObjectNode interview = (ObjectNode) evaluation.with("interviewAnalysis");
        JsonNode outInterview = out.path("interviewAnalysis");

        interview.set("voiceCoaching", arrayOrEmpty(outInterview.path("voiceCoaching")));

        mergeQuestionResponses(
                (ArrayNode) interview.withArray("questionResponses"),
                out.path("interviewAnalysis").path("questionResponses")
        );

        meta.put("llmStatus", "OK");
        meta.put("llmMergedAt", LocalDateTime.now().toString());
    }

    /**
     * topKeywords는 "딱 3개"를 원하니 normalize 해줌.
     * - 부족하면 있는 것만 (빈 문자열은 제거)
     * - 초과하면 앞 3개만
     * - 서버가 기본 키워드 주입하는 폴백은 금지 (빈 배열 가능)
     */
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

    /**
     * 모델명 기록용 (원하면 삭제)
     * InterviewOpenAiService에 model getter가 없으면 그냥 "unknown" 반환
     */
    private String modelNameOrUnknown() {
        return "unknown";
    }

    /**
     * 에러메시지 너무 길면 잘라서 예외 메시지에 넣기
     */
    private String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    // ─────────────────────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────────────────────

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

    // 한국어/영어 섞여도 대충 단어 수 세기(차트용)
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

    private int computeOverallFromQuestionResponses(JsonNode questionResponses) {
        if (questionResponses == null || !questionResponses.isArray()) return 0;

        int sum = 0;
        int count = 0;

        for (JsonNode item : questionResponses) {
            int score = clamp0_100(item.path("score").asInt(0));
            if (score > 0) {
                sum += score;
                count++;
            }
        }

        if (count == 0) return 0;
        return clamp0_100((int) Math.round(sum * 1.0 / count));
    }

    private void mergeQuestionResponses(ArrayNode baseArr, JsonNode outArr) {
        if (baseArr == null || !baseArr.isArray()) return;
        if (outArr == null || !outArr.isArray()) {
            log.warn("[LLM MERGE] outArr is empty or not array");
            return;
        }

        int mergedCount = 0;

        java.util.Map<Integer, JsonNode> byTurnNo = new java.util.HashMap<>();
        for (JsonNode node : outArr) {
            int turnNo = node.path("turnNo").asInt(0);
            if (turnNo > 0) {
                byTurnNo.put(turnNo, node);
            }
        }

        for (JsonNode node : baseArr) {
            if (!(node instanceof ObjectNode base)) continue;

            int turnNo = base.path("turnNo").asInt(0);
            JsonNode llmNode = byTurnNo.get(turnNo);
            if (llmNode == null || !llmNode.isObject()) continue;

            int beforeScore = base.path("score").asInt(0);

            int score = clamp0_100(llmNode.path("score").asInt(0));
            if (score > 0) {
                base.put("score", score);
            }

            String oneLine = llmNode.path("oneLineFeedback").asText("").trim();
            if (!oneLine.isBlank()) {
                base.put("oneLineFeedback", oneLine);
            }

            String full = llmNode.path("fullFeedback").asText("").trim();
            if (!full.isBlank()) {
                base.put("fullFeedback", full);
            }

            if (beforeScore != base.path("score").asInt(0) || !oneLine.isBlank() || !full.isBlank()) {
                mergedCount++;
            }
        }

        log.info("[LLM MERGE] merged questionResponses={}/{}", mergedCount, baseArr.size());
    }

    private ObjectNode emptyCompetencyBlock() {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("current", 0);
        block.put("target", 0);
        block.set("details", objectMapper.createObjectNode());
        return block;
    }

    private ObjectNode buildTechnicalCompetency(int overall) {
        int current = clamp0_100(overall);
        int target = clamp0_100(Math.max(current + 10, 80));

        ObjectNode details = objectMapper.createObjectNode();
        details.put("frontEnd", clamp0_100(current - 15));
        details.put("backEnd", clamp0_100(current + 5));
        details.put("database", clamp0_100(current));
        details.put("deployment", clamp0_100(current - 3));

        ObjectNode block = objectMapper.createObjectNode();
        block.put("current", current);
        block.put("target", target);
        block.set("details", details);
        return block;
    }

    private ObjectNode buildSoftCompetency(int overall) {
        int current = clamp0_100(Math.max(overall - 3, 0));
        int target = clamp0_100(Math.max(current + 10, 80));

        ObjectNode details = objectMapper.createObjectNode();
        details.put("communication", clamp0_100(current + 2));
        details.put("teamwork", clamp0_100(current - 2));
        details.put("leadership", clamp0_100(current - 6));
        details.put("presentation", clamp0_100(current));

        ObjectNode block = objectMapper.createObjectNode();
        block.put("current", current);
        block.put("target", target);
        block.set("details", details);
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

}
