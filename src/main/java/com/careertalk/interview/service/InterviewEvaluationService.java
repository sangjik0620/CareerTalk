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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewOpenAiService interviewOpenAiService; // ✅ 인터뷰 전용 1회 호출 서비스
    private final ObjectMapper objectMapper;

    /**
     * ✅ 분석 파이프라인 (유일한 생성 지점)
     * - 세션/턴 로드
     * - 서버는 "수치/구조"만 생성
     * - LLM 1회 호출로 "텍스트/액션/키워드/보이스 코칭" 생성
     * - DB upsert(result_json)
     */
    @Transactional
    public void runAnalysisInternal(Long sessionId) throws Exception {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // 1) 서버 계산/구조 생성 (텍스트 생성 금지)
        ObjectNode evaluation = buildEvaluationSkeleton(session, turns);

        // 2) LLM 1회 호출 → 프론트 키 이름으로 patch 생성
        applyLlmInsights(evaluation, session, turns);

        // 3) overallScore 등 저장용 요약 값 계산 (LLM이 overallScore를 주면 그걸 우선 사용)
        int overall = clamp0_100(evaluation.path("summary").path("overallScore").asInt(0));

        String jsonStr = safeWrite(evaluation);
        String strengths = joinArray(evaluation.path("summary").path("strengths"));
        String weaknesses = joinArray(evaluation.path("summary").path("weaknesses"));
        String nextActions = joinNextActions(evaluation.path("summary").path("nextActions"));

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
        return (status == null || status.isBlank()) ? "PENDING" : status;
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
        int total = Math.max(turns.size(), 1);
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
            // 질문 출력이 필요 없으면 프론트에서 숨기면 됨. (서버는 DB값 그대로 제공)
            one.put("question", safe(t.getAiQuestion()));
            one.put("answer", answer);

            // ✅ 여기서 서버가 feedback 텍스트를 만들지 않는다.
            one.put("score", 0);
            one.put("oneLineFeedback", "");
            one.put("fullFeedback", "");

            // (선택) 원본 지표를 노출하고 싶으면 아래처럼 raw로 묶어도 됨 (프론트가 안 쓰면 제거 가능)
            // one.set("audioScores", parseJsonOrNull(t.getAudioScoresJson()));
            // one.set("audioMetrics", parseJsonOrNull(t.getAudioMetricsJson()));
            // one.set("pythonMetrics", parseJsonOrNull(t.getPythonMetricsJson()));
        }

        // summary (수치 위주)
        ObjectNode summary = root.putObject("summary");
        summary.put("answeredQuestions", answered);
        summary.put("totalQuestions", total);
        summary.put("completionRate", total == 0 ? 0 : clamp0_100((int) Math.round(answered * 100.0 / total)));

        // 간단한 수치 지표(필요 시 프론트 차트용)
        int avgRespSec = respSecCount == 0 ? 0 : (int) Math.round(totalRespSec * 1.0 / respSecCount);
        summary.put("avgResponseSec", Math.max(0, avgRespSec));
        summary.put("totalWordCount", Math.max(0, totalWords));

        // overallScore: 서버가 임의 텍스트/룰로 만들지 않음. (LLM 또는 별도 점수 산식이 있으면 거기서)
        summary.put("overallScore", 0);
        summary.put("percentileRank", 0);

        // ✅ 텍스트 필드는 비워둠: LLM이 반드시 채움
        summary.set("topKeywords", objectMapper.createArrayNode());
        summary.set("strengths", objectMapper.createArrayNode());
        summary.set("weaknesses", objectMapper.createArrayNode());
        summary.set("nextActions", objectMapper.createArrayNode());

        // competency
        ObjectNode competency = root.putObject("competency");
        // ✅ improvements도 서버에서 actionItems 생성 금지: LLM이 채움
        competency.set("improvements", objectMapper.createArrayNode());

        // comparison (프론트가 쓰는 탭이 있으면 구조만 만들어 둠)
        ObjectNode comparison = root.putObject("comparison");
        comparison.put("percentileRank", 0);
        comparison.set("scoreHistory", objectMapper.createArrayNode());
        comparison.set("categoryComparison", objectMapper.createObjectNode());

        // interviewAnalysis.voiceCoaching도 LLM이 채움
        interview.set("voiceCoaching", objectMapper.createArrayNode());

        return root;
    }

    // ─────────────────────────────────────────────────────────────
    // 2) LLM 1회 호출 + merge (서버 템플릿/폴백 금지)
    // ─────────────────────────────────────────────────────────────

    private void applyLlmInsights(ObjectNode evaluation, InterviewSession session, List<InterviewTurn> turns) {
        // LLM 입력 payload (토큰 절약: 필요한 것만)
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sessionId", session.getSessionId());
        payload.put("position", safe(session.getTitle()));
        payload.put("createdAt", session.getCreatedAt() == null ? "" : session.getCreatedAt().toString());

        ArrayNode tArr = payload.putArray("turns");
        for (InterviewTurn t : turns) {
            ObjectNode one = tArr.addObject();
            one.put("turnNo", safeInt(t.getTurnNo(), 0));
            // 질문은 굳이 안 넣어도 됨(너가 지웠다 했으니). 필요 시만 살려.
            // one.put("question", safe(t.getAiQuestion()));
            one.put("answer", safe(firstNonBlank(t.getUserAnswerText(), t.getSttText(), "")).trim());
            one.put("durationSec", safeInt(t.getAnswerAudioDurationSec(), 0));
        }

        // ✅ system prompt: "JSON만" + "키 이름 고정"
        String system = """
                당신은 모의 면접 결과를 정리하는 평가 엔진이다.
                출력은 반드시 JSON '단독'으로만 반환하라. (설명문/마크다운/코드블록 금지)
                한국어로 작성하라.

                반드시 아래 스키마의 키 이름을 그대로 사용하라. 누락된 키는 빈 배열/빈 문자열로 채워라.

                {
                  "summary": {
                    "topKeywords": ["string","string","string"],
                    "strengths": ["string", ...],
                    "weaknesses": ["string", ...],
                    "nextActions": [{"title":"string","dueDays":number}, ...]
                  },
                  "competency": {
                    "improvements": [{
                      "area":"string",
                      "priority":"high|medium|low",
                      "currentLevel": number,
                      "targetLevel": number,
                      "actionItems": ["string", ...]
                    }, ...]
                  },
                  "interviewAnalysis": {
                    "voiceCoaching": ["string", ...]
                  }
                }

                제약:
                - nextActions/improvements.actionItems/topKeywords/voiceCoaching 는 서버 템플릿이 아니라 모델이 스스로 생성한다.
                - dueDays는 0 이상의 정수.
                - currentLevel/targetLevel은 0~100.
                """;

        String user = "면접 원자료:\n" + safeWrite(payload);

        JsonNode out;
        try {
            out = interviewOpenAiService.callJsonOnly(system, user);
        } catch (Exception e) {
            // ✅ 실패 시 서버 하드코딩으로 대체하지 않는다 (빈 값 유지)
            return;
        }

        // summary merge
        ObjectNode summary = (ObjectNode) evaluation.with("summary");
        JsonNode outSummary = out.path("summary");
        summary.set("topKeywords", arrayOrEmpty(outSummary.path("topKeywords")));
        summary.set("strengths", arrayOrEmpty(outSummary.path("strengths")));
        summary.set("weaknesses", arrayOrEmpty(outSummary.path("weaknesses")));
        summary.set("nextActions", normalizeNextActions(outSummary.path("nextActions")));

        // competency.improvements merge
        ObjectNode competency = (ObjectNode) evaluation.with("competency");
        JsonNode outComp = out.path("competency");
        competency.set("improvements", normalizeImprovements(outComp.path("improvements")));

        // interviewAnalysis.voiceCoaching merge
        ObjectNode interview = (ObjectNode) evaluation.with("interviewAnalysis");
        interview.set("voiceCoaching", arrayOrEmpty(out.path("interviewAnalysis").path("voiceCoaching")));

        // (선택) overallScore를 LLM이 계산해주길 원하면 system 스키마에 overallScore 추가하고 여기서 merge하면 됨.
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
}