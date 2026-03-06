package com.careertalk.interview.service;

import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewEvaluationRepository;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewTurnFeedbackService turnFeedbackService;
    private final ObjectMapper objectMapper;


    @Transactional
    public JsonNode getOrCreateEvaluationResultJson(Long sessionId) {
        String saved = evaluationRepository.findResultJsonBySessionId(sessionId);
        if (saved != null && !saved.isBlank()) {
            try {
                return objectMapper.readTree(saved);
            } catch (Exception ignored) {
            }
        }

        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        //  1) STT 완료된 turn은 feedback_json 비어있으면 turn 분석 수행
        for (InterviewTurn t : turns) {
            boolean hasStt = t.getSttText() != null && !t.getSttText().isBlank();
            boolean noFeedback = t.getFeedbackJson() == null || t.getFeedbackJson().isBlank();
            if (hasStt && noFeedback) {
                turnFeedbackService.analyzeAndSaveTurnFeedback(t);
            }
        }

        //  2) turn feedback 기반으로 evaluation JSON 생성
        ObjectNode json = buildEvaluationFromTurns(session, turns);
        applyRealStats(json, sessionId, session);

        int overall = json.path("summary").path("overallScore").asInt(0);

        String jsonStr;
        try {
            jsonStr = objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("serialize failed", e);
        }

        String strengths = joinArray(json.path("summary").path("strengths"));
        String weaknesses = joinArray(json.path("summary").path("weaknesses"));
        String nextActions = joinNextActions(json.path("summary").path("nextActions"));

        evaluationRepository.upsert(sessionId, overall, strengths, weaknesses, nextActions, jsonStr);
        return json;
    }

    @Transactional
    public void runAnalysisInternal(Long sessionId) throws Exception {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        for (InterviewTurn t : turns) {
            boolean hasStt = t.getSttText() != null && !t.getSttText().isBlank();
            boolean noFeedback = t.getFeedbackJson() == null || t.getFeedbackJson().isBlank();
            if (hasStt && noFeedback) {
                turnFeedbackService.analyzeAndSaveTurnFeedback(t);
            }
        }

        ObjectNode json = buildEvaluationFromTurns(session, turns);
        applyRealStats(json, sessionId, session);

        int overall = json.path("summary").path("overallScore").asInt(0);

        String jsonStr = objectMapper.writeValueAsString(json);
        String strengths = joinArray(json.path("summary").path("strengths"));
        String weaknesses = joinArray(json.path("summary").path("weaknesses"));
        String nextActions = joinNextActions(json.path("summary").path("nextActions"));

        evaluationRepository.upsert(sessionId, overall, strengths, weaknesses, nextActions, jsonStr);
    }

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

    private ObjectNode buildEvaluationFromTurns(InterviewSession session, List<InterviewTurn> turns) {
        ObjectNode root = objectMapper.createObjectNode();

        // 1) interviewInfo
        ObjectNode interviewInfo = root.putObject("interviewInfo");
        interviewInfo.put("date", session.getCreatedAt().toLocalDate().toString());
        interviewInfo.put("duration", formatDuration(session.getStartedAt(), session.getEndedAt()));

        String position = null;
//         나중에 DB 컬럼 생기면 이 줄만 바꾸면 됨
//        position = session.getPosition();
        position = session.getTitle(); // 임시 fallback

        if (position != null && !position.isBlank()) {
            interviewInfo.put("position", position.trim());
        }

        // 2) turn feedback 파싱해서 questionResponses 만들기 + 점수/키워드 집계
        int answeredQ = 0;
        int totalQ = Math.max(turns.size(), 1);

        int scoreSum = 0;
        int scoreCount = 0;

        int totalWords = 0;
        double avgRespSecSum = 0;
        int avgRespCount = 0;

        int sentimentSum = 0;
        int sentimentCount = 0;

        int kwTech = 0, kwSoft = 0, kwCompany = 0;

        ObjectNode interview = root.putObject("interviewAnalysis");
        var qArr = interview.putArray("questionResponses");

        int missingCnt = 0;
        int repeatPenalty = 0;
        int vaguePenalty = 0;
        int offTopicPenalty = 0;
        int longPenalty = 0;

        for (InterviewTurn t : turns) {
            String resp = firstNonBlank(t.getUserAnswerText(), t.getSttText(), "");
            if (!resp.isBlank()) answeredQ++;

            int wordCount = countWords(resp);
            totalWords += wordCount;

            Integer dur = t.getAnswerAudioDurationSec();
            if (dur != null && dur > 0) {
                avgRespSecSum += dur;
                avgRespCount++;
            }

            int score = 0;
            String oneLine = "답변을 더 구체화해보세요.";

            if (t.getFeedbackJson() != null && !t.getFeedbackJson().isBlank()) {
                try {
                    JsonNode fb = objectMapper.readTree(t.getFeedbackJson());
                    score = clamp(fb.path("score").asInt(0));
                    oneLine = fb.path("feedback").asText(oneLine);

                    scoreSum += score;
                    scoreCount++;

                    int sent = clamp(fb.path("sentimentScore").asInt(0));
                    if (sent > 0) {
                        sentimentSum += sent;
                        sentimentCount++;
                    }

                    kwTech += fb.path("keywords").path("technical").isArray()
                            ? fb.path("keywords").path("technical").size() : 0;
                    kwSoft += fb.path("keywords").path("soft").isArray()
                            ? fb.path("keywords").path("soft").size() : 0;
                    kwCompany += fb.path("keywords").path("company").isArray()
                            ? fb.path("keywords").path("company").size() : 0;

                    // logic.missing 카운트
                    JsonNode missing = fb.path("logic").path("missing");
                    if (missing.isArray()) missingCnt += missing.size();

                    // penalty 카운트
                    JsonNode penalties = fb.path("penalty");
                    if (penalties.isArray()) {
                        for (JsonNode p : penalties) {
                            String type = p.path("type").asText("");
                            switch (type) {
                                case "REPEAT" -> repeatPenalty++;
                                case "VAGUE" -> vaguePenalty++;
                                case "OFFTOPIC" -> offTopicPenalty++;
                                case "LONG" -> longPenalty++;
                            }
                        }
                    }

                } catch (Exception ignored) {
                }
            }


            ObjectNode q = qArr.addObject();
            q.put("question", safe(t.getAiQuestion(), "질문 " + t.getTurnNo()));
            q.put("response", resp.isBlank() ? "(답변 없음)" : resp);
            q.put("score", score);
            q.put("feedback", oneLine);
            q.put("duration", dur != null ? dur : 0);
        }

        int overall = scoreCount > 0 ? Math.round((float) scoreSum / scoreCount) : 0;

        // 3) summary (프론트에서 제일 많이 씀)
        ObjectNode summary = root.putObject("summary");
        summary.put("overallScore", overall);

        summary.set("strengths", buildStrengths(turns));
        summary.set("weaknesses", buildWeaknesses(turns));
        summary.set("topKeywords", buildTopKeywords(turns, 3));
        summary.set("nextActions", buildNextActions(summary.path("weaknesses")));

        summary.put("totalQuestions", totalQ);
        summary.put("answeredQuestions", answeredQ);

        summary.put("verdict", pickVerdict(overall));

        // NOTE: technical/communication/problem/leadership index는 buildCompetency가 실데이터 파생으로 덮어씀

        double avgResp = avgRespCount > 0 ? (avgRespSecSum / avgRespCount) : 0;
        summary.put("avgResponseTimeSec", round1(avgResp));

        summary.put("fillerWordRate", round1(Math.min(15.0,
                (totalWords > 0 ? (double) countFillersAll(turns) / totalWords * 100.0 : 0)
        )));
        summary.put("sentimentScore", sentimentCount > 0 ? Math.round((float) sentimentSum / sentimentCount) : 0);

        // 4) competency (summary가 생성된 뒤에 만들어야 함: buildCompetency가 summary/weaknesses를 참조)
        ObjectNode competency = buildCompetency(turns, root);
        root.set("competency", competency);

        // 5) voiceMetrics / sttAnalysis (프론트가 기대)
        ObjectNode voice = interview.putObject("voiceMetrics");
        ObjectNode va = buildVoiceAnalysis(turns);

        int confidenceIndex = computeConfidenceIndex(
                va,
                summary.path("fillerWordRate").asDouble(0),
                summary.path("sentimentScore").asInt(0),
                overall,
                vaguePenalty,
                repeatPenalty
        );
        summary.put("confidenceIndex", confidenceIndex);

        int jobFitIndex = computeJobFitIndex(
                overall,
                kwTech, kwCompany, kwSoft,
                offTopicPenalty, vaguePenalty, longPenalty, repeatPenalty,
                missingCnt
        );
        summary.put("jobFitIndex", jobFitIndex);

        voice.put("clarity", va.path("clarity").asInt(0));
        voice.put("pace", va.path("speechRate").asInt(0));
        voice.put("volume", va.path("volume").asInt(0));
        voice.put("confidence", va.path("confidence").asInt(0));
        voice.put("fillerWords", va.path("fillerCount").asInt(0));

        ObjectNode stt = interview.putObject("sttAnalysis");
        stt.put("totalWords", totalWords);
        stt.put("averageResponseTime", round1(avgResp));

        ObjectNode usage = stt.putObject("keywordUsage");
        usage.put("technical", kwTech);
        usage.put("soft", kwSoft);
        usage.put("company", kwCompany);

        stt.put("sentimentScore", sentimentCount > 0 ? Math.round((float) sentimentSum / sentimentCount) : 0);

        // 6) comparison
        root.set("comparison", buildComparison(session, root));

        return root;
    }

    private int countWords(String s) {
        if (s == null || s.isBlank()) return 0;
        return s.trim().split("\\s+").length;
    }

    private int countFillersAll(List<InterviewTurn> turns) {
        int sum = 0;
        for (InterviewTurn t : turns) {
            String resp = firstNonBlank(t.getUserAnswerText(), t.getSttText(), "");
            sum += countFillers(resp);
        }
        return sum;
    }

    private ObjectNode triple(int user, int avg, int prev) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("user", clamp(user));
        n.put("average", clamp(avg));
        n.put("previous", clamp(prev));
        return n;
    }

    private String formatDuration(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt != null && endedAt != null) {
            long sec = Duration.between(startedAt, endedAt).getSeconds();
            long min = Math.max(1, sec / 60);
            return min + "분";
        }
        return "시간 정보 없음";
    }

    private String pickVerdict(int overall) {
        if (overall >= 82) return "합격권";
        if (overall >= 70) return "보류";
        return "개선필요";
    }

    private String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        return toStream(arr).collect(Collectors.joining("\n"));
    }

    private String joinNextActions(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : arr) {
            String title = n.path("title").asText("");
            int due = n.path("dueDays").asInt(0);
            if (!title.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(title).append(" (D-").append(due).append(")");
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private java.util.stream.Stream<String> toStream(JsonNode arr) {
        java.util.Iterator<JsonNode> it = arr.elements();
        java.util.List<String> out = new java.util.ArrayList<>();
        while (it.hasNext()) out.add(it.next().asText());
        return out.stream();
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s, String fallback) {
        return notBlank(s) ? s : fallback;
    }

    private String firstNonBlank(String a, String b, String fallback) {
        if (notBlank(a)) return a;
        if (notBlank(b)) return b;
        return fallback;
    }

    private void applyRealStats(ObjectNode json, Long sessionId, InterviewSession session) {
        ObjectNode summary = (ObjectNode) json.path("summary");
        if (summary == null || summary.isMissingNode() || summary.isNull()) return;

        int overall = summary.path("overallScore").asInt(0);

        // 1) 이전 점수
        Integer prev = null;
        if (session.getCreatedAt() != null) {
            prev = evaluationRepository.findPrevOverallScore(session.getUserId(), session.getCreatedAt());
        }
        summary.put("previousScore", prev == null ? overall : prev);

        // 2) 전체 평균
        Double avg = evaluationRepository.findGlobalAverageScoreExcludingSession(sessionId);
        summary.put("passedAverage", avg == null ? overall : (int) Math.round(avg));

        // 3) 백분위(Percentile)
        int pctInt = 50;
        try {
            Double pct = evaluationRepository.findPercentileRankExcludingSession(sessionId, overall);
            if (pct != null) pctInt = (int) Math.round(pct);
        } catch (Exception ignored) {
        }

        summary.put("percentileRank", clamp(pctInt));

        JsonNode compNode = json.path("comparison");
        if (compNode != null && compNode.isObject()) {
            ((ObjectNode) compNode).put("percentileRank", clamp(pctInt));
        }
    }

    private ObjectNode buildVoiceAnalysis(List<InterviewTurn> turns) {
        ObjectNode voice = objectMapper.createObjectNode();

        double speechRateSum = 0;
        double volumeSum = 0;
        int metricsCount = 0;

        double confidenceSum = 0;
        double claritySum = 0;
        int scoresCount = 0;

        int fillerCount = 0;

        for (InterviewTurn t : turns) {
            fillerCount += countFillers(firstNonBlank(t.getUserAnswerText(), t.getSttText(), ""));

            if (t.getAudioMetricsJson() != null && !t.getAudioMetricsJson().isBlank()) {
                try {
                    JsonNode metrics = objectMapper.readTree(t.getAudioMetricsJson());
                    speechRateSum += metrics.path("speechRateWps").asDouble(0);
                    volumeSum += metrics.path("meanVolumeDb").asDouble(0);
                    metricsCount++;
                } catch (Exception ignored) {
                }
            }

            if (t.getAudioScoresJson() != null && !t.getAudioScoresJson().isBlank()) {
                try {
                    JsonNode scores = objectMapper.readTree(t.getAudioScoresJson());
                    confidenceSum += scores.path("confidenceScore").asDouble(0);
                    claritySum += scores.path("fluencyScore").asDouble(0);
                    scoresCount++;
                } catch (Exception ignored) {
                }
            }
        }

        double speechRate = metricsCount > 0 ? speechRateSum / metricsCount : 0;
        double volumeDb = metricsCount > 0 ? volumeSum / metricsCount : 0;
        double confidence = scoresCount > 0 ? confidenceSum / scoresCount : 0;
        double clarity = scoresCount > 0 ? claritySum / scoresCount : 0;

        int speechRateScore = normalizeSpeechRate(speechRate);
        int volumeScore = normalizeVolume(volumeDb);

        voice.put("speechRate", speechRateScore);
        voice.put("volume", volumeScore);
        voice.put("clarity", clamp((int) Math.round(clarity)));
        voice.put("confidence", clamp((int) Math.round(confidence)));
        voice.put("fillerCount", fillerCount);

        return voice;
    }

    private int countFillers(String text) {
        if (text == null) return 0;

        String[] fillers = {"어", "음", "그", "저", "막", "약간", "그러니까", "그니까"};
        int count = 0;
        for (String f : fillers) {
            count += text.split(f, -1).length - 1;
        }
        return count;
    }

    private int normalizeSpeechRate(double wps) {
        double score = (wps / 5.0) * 100;
        if (score > 100) score = 100;
        if (score < 0) score = 0;
        return (int) score;
    }

    private int normalizeVolume(double db) {
        double normalized = (db + 30) * 5;
        if (normalized > 100) normalized = 100;
        if (normalized < 0) normalized = 0;
        return (int) normalized;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildWeaknesses(List<InterviewTurn> turns) {
        var counts = new java.util.LinkedHashMap<String, Integer>();

        for (InterviewTurn t : turns) {
            String fj = t.getFeedbackJson();
            if (fj == null || fj.isBlank()) continue;

            try {
                JsonNode fb = objectMapper.readTree(fj);

                JsonNode missing = fb.path("logic").path("missing");
                if (missing.isArray() && missing.size() > 0) {
                    bump(counts, "답변 구조화");
                    for (JsonNode m : missing) {
                        String s = m.asText("").trim();
                        if (!s.isEmpty()) bump(counts, "논리 요소 누락(" + s + ")");
                    }
                }

                JsonNode penalties = fb.path("penalty");
                if (penalties.isArray()) {
                    for (JsonNode p : penalties) {
                        String type = p.path("type").asText("");
                        switch (type) {
                            case "REPEAT" -> bump(counts, "반복 표현");
                            case "VAGUE" -> bump(counts, "구체성 부족");
                            case "OFFTOPIC" -> bump(counts, "질문 의도 이탈");
                            case "LONG" -> bump(counts, "장황함");
                            default -> {
                                if (!type.isBlank()) bump(counts, "표현 개선(" + type + ")");
                            }
                        }
                    }
                }

                int score = fb.path("score").asInt(-1);
                if (score >= 0 && score < 40) bump(counts, "답변 완성도 낮음");

            } catch (Exception ignored) {
            }
        }

        if (counts.isEmpty()) {
            bump(counts, "답변 구조화");
            bump(counts, "구체성 부족");
        }

        return topLabels(counts, 3);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildStrengths(List<InterviewTurn> turns) {
        var counts = new java.util.LinkedHashMap<String, Integer>();

        for (InterviewTurn t : turns) {
            String fj = t.getFeedbackJson();
            if (fj == null || fj.isBlank()) continue;

            try {
                JsonNode fb = objectMapper.readTree(fj);

                int score = fb.path("score").asInt(-1);
                boolean hasMissing = fb.path("logic").path("missing").isArray()
                        && fb.path("logic").path("missing").size() > 0;
                boolean hasPenalty = fb.path("penalty").isArray()
                        && fb.path("penalty").size() > 0;
                int sentiment = fb.path("sentimentScore").asInt(-1);

                if (score >= 70) bump(counts, "핵심 전달");
                if (!hasMissing) bump(counts, "답변 구조화");
                if (!hasPenalty && score >= 60) bump(counts, "표현의 깔끔함");
                if (sentiment >= 60) bump(counts, "긍정적 태도");

            } catch (Exception ignored) {
            }
        }

        if (counts.isEmpty()) {
            bump(counts, "핵심 전달");
            bump(counts, "답변 구조화");
        }

        return topLabels(counts, 3);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildTopKeywords(List<InterviewTurn> turns, int topN) {
        var counts = new java.util.HashMap<String, Integer>();

        for (InterviewTurn t : turns) {
            String fj = t.getFeedbackJson();
            if (fj == null || fj.isBlank()) continue;

            try {
                JsonNode fb = objectMapper.readTree(fj);
                JsonNode kw = fb.path("keywords");

                addKeywords(counts, kw.path("technical"));
                addKeywords(counts, kw.path("soft"));
                addKeywords(counts, kw.path("company"));

            } catch (Exception ignored) {
            }
        }

        if (counts.isEmpty()) {
            var arr = objectMapper.createArrayNode();
            arr.add("프로젝트").add("협업").add("문제해결");
            return arr;
        }

        return topLabels(counts, topN);
    }

    private void addKeywords(java.util.Map<String, Integer> counts, JsonNode arr) {
        if (!arr.isArray()) return;
        for (JsonNode n : arr) {
            String k = n.asText("").trim();
            if (!k.isEmpty()) counts.put(k, counts.getOrDefault(k, 0) + 1);
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildNextActions(JsonNode weaknesses) {
        var next = objectMapper.createArrayNode();
        var added = new java.util.HashSet<String>();

        java.util.List<String> ws = new java.util.ArrayList<>();
        if (weaknesses != null && weaknesses.isArray()) {
            for (JsonNode w : weaknesses) ws.add(w.asText(""));
        }

        for (String w : ws) {
            if (w.contains("구조화") || w.contains("논리")) {
                addAction(next, added, "STAR 템플릿으로 10문항 연습", 7);
                addAction(next, added, "결론 1문장 + 근거 2개로 답변 구성", 3);
            }
            if (w.contains("구체성")) {
                addAction(next, added, "성과/수치화 사례 5개 정리", 5);
            }
            if (w.contains("반복")) {
                addAction(next, added, "반복 표현 대체 문장 10개 준비", 3);
            }
            if (w.contains("장황")) {
                addAction(next, added, "답변 30초/60초 버전으로 압축 연습", 3);
            }
            if (w.contains("의도")) {
                addAction(next, added, "질문을 한 문장으로 재정의 후 답변 시작", 3);
            }
        }

        if (next.isEmpty()) {
            addAction(next, added, "답변 결론 1문장 습관화", 3);
            addAction(next, added, "STAR 템플릿으로 10문항 연습", 7);
        }

        while (next.size() > 3) next.remove(next.size() - 1);
        return next;
    }

    private void addAction(com.fasterxml.jackson.databind.node.ArrayNode next,
                           java.util.Set<String> added,
                           String title, int dueDays) {
        if (added.add(title)) next.addObject().put("title", title).put("dueDays", dueDays);
    }

    private void bump(java.util.Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode topLabels(java.util.Map<String, Integer> counts, int topN) {
        var arr = objectMapper.createArrayNode();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> arr.add(e.getKey()));
        return arr;
    }

    private ObjectNode buildComparison(InterviewSession session, ObjectNode root) {
        ObjectNode comp = objectMapper.createObjectNode();

        int pct = root.path("summary").path("percentileRank").asInt(0);
        comp.put("percentileRank", clamp(pct));

        var histArr = comp.putArray("scoreHistory");
        try {
            var rows = evaluationRepository.findRecentScoreHistory(session.getUserId(), 6);
            java.util.Collections.reverse(rows);
            for (Object[] r : rows) {
                String ym = String.valueOf(r[0]);
                int score = r[1] == null ? 0 : ((Number) r[1]).intValue();
                histArr.addObject().put("date", ym).put("score", clamp(score));
            }
        } catch (Exception ignored) {
        }

        ObjectNode cat = comp.putObject("categoryComparison");

        int tech = root.path("summary").path("technicalIndex").asInt(0);
        int comm = root.path("summary").path("communicationIndex").asInt(0);
        int prob = root.path("summary").path("problemIndex").asInt(0);
        int lead = root.path("summary").path("leadershipIndex").asInt(0);

        int avgTech = safeAvg(session.getSessionId(), "$.summary.technicalIndex");
        int avgComm = safeAvg(session.getSessionId(), "$.summary.communicationIndex");
        int avgProb = safeAvg(session.getSessionId(), "$.summary.problemIndex");
        int avgLead = safeAvg(session.getSessionId(), "$.summary.leadershipIndex");

        String prevJson = null;
        try {
            prevJson = evaluationRepository.findPrevResultJson(session.getUserId(), session.getCreatedAt());
        } catch (Exception ignored) {
        }

        int prevTech = extractPrevIndex(prevJson, "technicalIndex", tech);
        int prevComm = extractPrevIndex(prevJson, "communicationIndex", comm);
        int prevProb = extractPrevIndex(prevJson, "problemIndex", prob);
        int prevLead = extractPrevIndex(prevJson, "leadershipIndex", lead);

        cat.set("technical", triple(clamp(tech), clamp(avgTech), clamp(prevTech)));
        cat.set("communication", triple(clamp(comm), clamp(avgComm), clamp(prevComm)));
        cat.set("problem", triple(clamp(prob), clamp(avgProb), clamp(prevProb)));
        cat.set("leadership", triple(clamp(lead), clamp(avgLead), clamp(prevLead)));

        return comp;
    }

    private int safeAvg(Long sessionId, String jsonPath) {
        try {
            Double d = evaluationRepository.avgFromResultJson(sessionId, jsonPath);
            if (d == null) return 0;
            return (int) Math.round(d);
        } catch (Exception e) {
            return 0;
        }
    }

    private int extractPrevIndex(String prevResultJson, String field, int fallback) {
        if (prevResultJson == null || prevResultJson.isBlank()) return fallback;
        try {
            JsonNode j = objectMapper.readTree(prevResultJson);
            return clamp(j.path("summary").path(field).asInt(fallback));
        } catch (Exception e) {
            return fallback;
        }
    }

    private ObjectNode buildCompetency(List<InterviewTurn> turns, ObjectNode root) {
        ObjectNode comp = objectMapper.createObjectNode();

        // summary는 buildEvaluationFromTurns에서 미리 생성됨
        ObjectNode summary = (ObjectNode) root.path("summary");

        // ===== 1) 기술 역량 =====
        ObjectNode tech = comp.putObject("technical");

        int fe = 0, be = 0, db = 0, dep = 0;
        int techKwTotal = 0;

        for (InterviewTurn t : turns) {
            String fj = t.getFeedbackJson();
            if (fj == null || fj.isBlank()) continue;
            try {
                JsonNode fb = objectMapper.readTree(fj);
                JsonNode arr = fb.path("keywords").path("technical");
                if (!arr.isArray()) continue;
                for (JsonNode n : arr) {
                    String k = n.asText("").toLowerCase();
                    if (k.isBlank()) continue;
                    techKwTotal++;

                    if (k.contains("react") || k.contains("vue") || k.contains("html") || k.contains("css")
                            || k.contains("javascript") || k.contains("typescript")) fe++;
                    else if (k.contains("spring") || k.contains("java") || k.contains("node") || k.contains("api") || k.contains("jwt"))
                        be++;
                    else if (k.contains("mysql") || k.contains("sql") || k.contains("db") || k.contains("database") || k.contains("redis"))
                        db++;
                    else if (k.contains("aws") || k.contains("docker") || k.contains("k8") || k.contains("ci") || k.contains("cd") || k.contains("deploy"))
                        dep++;
                }
            } catch (Exception ignored) {
            }
        }

        int overall = summary.path("overallScore").asInt(0);

        int techCurrent = clamp((int) Math.round(overall * 0.6 + Math.min(40, techKwTotal * 5)));
        tech.put("current", techCurrent);
        tech.put("target", 90);

        ObjectNode techD = tech.putObject("details");
        techD.put("frontEnd", clamp(fe * 12));
        techD.put("backEnd", clamp(be * 12));
        techD.put("database", clamp(db * 12));
        techD.put("deployment", clamp(dep * 12));

        // ===== 2) 소프트 스킬 =====
        ObjectNode soft = comp.putObject("soft");

        int missingCnt = 0;
        int repeatPenalty = 0;
        int vaguePenalty = 0;
        int longPenalty = 0;
        int offTopicPenalty = 0;
        int softKw = 0;
        int sentimentSum = 0, sentimentCount = 0;

        for (InterviewTurn t : turns) {
            String fj = t.getFeedbackJson();
            if (fj == null || fj.isBlank()) continue;
            try {
                JsonNode fb = objectMapper.readTree(fj);

                JsonNode missing = fb.path("logic").path("missing");
                if (missing.isArray()) missingCnt += missing.size();

                JsonNode penalties = fb.path("penalty");
                if (penalties.isArray()) {
                    for (JsonNode p : penalties) {
                        String type = p.path("type").asText("");
                        switch (type) {
                            case "REPEAT" -> repeatPenalty++;
                            case "VAGUE" -> vaguePenalty++;
                            case "LONG" -> longPenalty++;
                            case "OFFTOPIC" -> offTopicPenalty++;
                        }
                    }
                }

                JsonNode softArr = fb.path("keywords").path("soft");
                if (softArr.isArray()) softKw += softArr.size();

                int sent = fb.path("sentimentScore").asInt(0);
                if (sent > 0) {
                    sentimentSum += sent;
                    sentimentCount++;
                }

            } catch (Exception ignored) {
            }
        }

        int sentimentAvg = sentimentCount > 0 ? (int) Math.round((double) sentimentSum / sentimentCount) : 0;

        int comm = clamp(80 - missingCnt * 8 - (repeatPenalty + vaguePenalty + longPenalty + offTopicPenalty) * 4);
        int teamwork = clamp(30 + softKw * 8);
        int leadership = clamp(20 + softKw * 6 + (int) Math.round(sentimentAvg * 0.4));
        int presentation = clamp((int) Math.round(overall * 0.5 + 50 - longPenalty * 10 - repeatPenalty * 6));

        int softCurrent = clamp((comm + teamwork + leadership + presentation) / 4);
        soft.put("current", softCurrent);
        soft.put("target", 85);

        ObjectNode softD = soft.putObject("details");
        softD.put("communication", comm);
        softD.put("teamwork", teamwork);
        softD.put("leadership", leadership);
        softD.put("presentation", presentation);

        // ===== 3) improvements (weaknesses 기반) =====
        var improvements = comp.putArray("improvements");
        JsonNode weaknesses = summary.path("weaknesses");

        if (weaknesses != null && weaknesses.isArray() && weaknesses.size() > 0) {
            for (int i = 0; i < Math.min(2, weaknesses.size()); i++) {
                String w = weaknesses.get(i).asText("");
                if (w.isBlank()) continue;

                ObjectNode it = improvements.addObject();
                it.put("area", w);
                it.put("priority", i == 0 ? "high" : "medium");

                int cur = clamp(overall - (i * 5));
                it.put("currentLevel", cur);
                it.put("targetLevel", clamp(cur + 20));

                var act = it.putArray("actionItems");
                if (w.contains("구조") || w.contains("논리")) {
                    act.add("STAR 템플릿으로 10문항 작성");
                    act.add("결론 1문장 → 근거 2개 → 결과 1문장 구조로 답변");
                } else if (w.contains("구체")) {
                    act.add("성과/수치가 포함된 사례 5개 준비");
                    act.add("근거 없는 표현을 숫자/상황으로 치환");
                } else if (w.contains("반복")) {
                    act.add("자주 쓰는 반복 표현 리스트업 후 대체 표현 10개 준비");
                } else if (w.contains("장황")) {
                    act.add("답변 30초/60초 버전으로 요약 연습");
                } else {
                    act.add("문제 원인-해결-결과 형태로 3문장 요약 연습");
                }
            }
        }

        // ===== 4) comparison용 index를 summary에 실데이터로 저장 =====
        summary.put("technicalIndex", techCurrent);
        summary.put("communicationIndex", comm);
        summary.put("problemIndex", clamp(70 - offTopicPenalty * 10 - missingCnt * 5));
        summary.put("leadershipIndex", leadership);

        return comp;
    }

    private int computeConfidenceIndex(
            ObjectNode voiceAnalysis,     // buildVoiceAnalysis() 결과
            double fillerWordRate,        // summary.fillerWordRate (0~15 정도)
            int sentimentScore,           // summary.sentimentScore (0~100)
            int overallScore,             // summary.overallScore
            int vaguePenalty,
            int repeatPenalty
    ) {
        // 음성 confidence/clarity는 0~100 스케일로 들어옴(없으면 0)
        int vConf = clamp(voiceAnalysis.path("confidence").asInt(0));
        int vClarity = clamp(voiceAnalysis.path("clarity").asInt(0));
        int vPace = clamp(voiceAnalysis.path("speechRate").asInt(0));
        int vVolume = clamp(voiceAnalysis.path("volume").asInt(0));

        // fillerWordRate: 높을수록 감점 (0~15% 범위로 계산해둔 상태)
        int fillerPenalty = (int) Math.round(Math.min(30.0, fillerWordRate * 2.0)); // 최대 -30

        // 답변 품질 패널티 일부 반영 (자신감은 “주저/반복/애매함”에 민감)
        int textPenalty = Math.min(20, vaguePenalty * 4 + repeatPenalty * 3);

        // pace/volume은 “적정 범위” 선호 (너무 빠르거나 너무 조용하면 감점)
        int paceStability = 100 - Math.abs(vPace - 70);     // 70 근처를 적정으로 가정
        int volumeStability = 100 - Math.abs(vVolume - 65); // 65 근처를 적정으로 가정
        paceStability = clamp(paceStability);
        volumeStability = clamp(volumeStability);

        // 최종 스코어(가중합)
        double raw =
                0.40 * vConf +
                        0.20 * vClarity +
                        0.10 * paceStability +
                        0.10 * volumeStability +
                        0.15 * clamp(sentimentScore) +
                        0.05 * clamp(overallScore)
                        - fillerPenalty
                        - textPenalty;

        return clamp((int) Math.round(raw));
    }

    private int computeJobFitIndex(
            int overallScore,
            int kwTech, int kwCompany, int kwSoft,
            int offTopicPenalty, int vaguePenalty, int longPenalty, int repeatPenalty,
            int missingCnt
    ) {
        // 키워드: 기술/기업 키워드는 직무 적합도에 더 직접적, 소프트는 보조
        int kwScore = Math.min(60, kwTech * 6 + kwCompany * 10 + kwSoft * 3);

        // 패널티: 질문 의도 이탈/구체성 부족/장황함/반복/논리누락은 적합도에 큰 감점
        int penalty =
                offTopicPenalty * 12 +
                        vaguePenalty * 6 +
                        longPenalty * 5 +
                        repeatPenalty * 4 +
                        missingCnt * 3;

        penalty = Math.min(80, penalty);

        // overall은 “기본 베이스”
        double raw =
                0.60 * clamp(overallScore) +
                        0.40 * kwScore
                        - penalty;

        return clamp((int) Math.round(raw));
    }

}