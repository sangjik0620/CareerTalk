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
import org.springframework.transaction.annotation.Propagation;

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
            } catch (Exception ignored) {}
        }

        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // ✅ 1) STT 완료된 turn은 feedback_json 비어있으면 turn 분석 수행
        for (InterviewTurn t : turns) {
            boolean hasStt = t.getSttText() != null && !t.getSttText().isBlank();
            boolean noFeedback = t.getFeedbackJson() == null || t.getFeedbackJson().isBlank();
            if (hasStt && noFeedback) {
                turnFeedbackService.analyzeAndSaveTurnFeedback(t);
            }
        }

        // ✅ 2) turn feedback 기반으로 evaluation JSON 생성
        ObjectNode json = buildEvaluationFromTurns(session, turns);

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
        interviewInfo.put("date", LocalDate.now().toString());
        interviewInfo.put("duration", formatDuration(session.getStartedAt(), session.getEndedAt()));
        interviewInfo.put("position", safe(session.getTitle(), "프론트엔드 개발자"));
        interviewInfo.put("company", "ABC Tech");

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

                    kwTech += fb.path("keywords").path("technical").isArray() ? fb.path("keywords").path("technical").size() : 0;
                    kwSoft += fb.path("keywords").path("soft").isArray() ? fb.path("keywords").path("soft").size() : 0;
                    kwCompany += fb.path("keywords").path("company").isArray() ? fb.path("keywords").path("company").size() : 0;

                } catch (Exception ignored) {}
            }

            ObjectNode q = qArr.addObject();
            q.put("question", safe(t.getAiQuestion(), "질문 " + t.getTurnNo()));
            q.put("response", resp.isBlank() ? "(답변 없음)" : resp);
            q.put("score", score);
            q.put("feedback", oneLine);
            q.put("duration", dur != null ? dur : 0);
        }

        int overall = scoreCount > 0 ? Math.round((float) scoreSum / scoreCount) : 0;

        // 3) summary (프론트에서 제일 많이 씀) - 최소 충족 + 계산값 반영
        ObjectNode summary = root.putObject("summary");
        summary.put("overallScore", overall);
        summary.put("previousScore", clamp(overall - 5));
        summary.put("passedAverage", 78);

        summary.putArray("strengths").add("질문 이해도").add("핵심 전달");
        summary.putArray("weaknesses").add("답변 구조화").add("구체적 사례 부족");

        summary.put("totalQuestions", totalQ);
        summary.put("answeredQuestions", answeredQ);

        summary.put("verdict", pickVerdict(overall));
        summary.put("percentileRank", clamp(60)); // 임시. 필요하면 추후 통계로.

        summary.put("confidenceIndex", clamp(overall));
        summary.put("jobFitIndex", clamp(overall - 2));
        summary.put("technicalIndex", clamp(overall + 3));
        summary.put("communicationIndex", clamp(overall - 4));

        double avgResp = avgRespCount > 0 ? (avgRespSecSum / avgRespCount) : 0;
        summary.put("avgResponseTimeSec", round1(avgResp));

        // fillerWordRate는 정교하게 하려면 룰 기반 카운팅 필요. 지금은 간단 추정(나중에 개선)
        summary.put("fillerWordRate", round1(Math.min(15.0, (totalWords > 0 ? (double) countFillersAll(turns) / totalWords * 100.0 : 0))));
        summary.put("sentimentScore", sentimentCount > 0 ? Math.round((float) sentimentSum / sentimentCount) : 0);

        summary.putArray("topKeywords").add("프로젝트").add("협업").add("문제해결");

        var next = summary.putArray("nextActions");
        next.addObject().put("title", "답변 결론 1문장 습관화").put("dueDays", 3);
        next.addObject().put("title", "성과 수치/사례 5개 정리").put("dueDays", 5);
        next.addObject().put("title", "STAR 템플릿으로 10문항 연습").put("dueDays", 7);

        // 4) interviewAnalysis.voiceMetrics / sttAnalysis (프론트가 기대)
        // voiceMetrics는 지금 소스에 audio_scores_json 같은 게 있으니 추후 연동 가능.
        // 일단 최소 값 채움(나중에 audio_scores_json에서 파싱해서 넣으면 됨)
        ObjectNode voice = interview.putObject("voiceMetrics");
        voice.put("clarity", clamp(overall - 3));
        voice.put("pace", clamp(overall - 6));
        voice.put("volume", clamp(overall - 4));
        voice.put("confidence", clamp(overall - 2));
        voice.put("fillerWords", countFillersAll(turns));

        ObjectNode stt = interview.putObject("sttAnalysis");
        stt.put("totalWords", totalWords);
        stt.put("averageResponseTime", round1(avgResp));

        ObjectNode usage = stt.putObject("keywordUsage");
        usage.put("technical", kwTech);
        usage.put("soft", kwSoft);
        usage.put("company", kwCompany);

        stt.put("sentimentScore", sentimentCount > 0 ? Math.round((float) sentimentSum / sentimentCount) : 0);

        // 5) 나머지 탭(document/comparison/competency)은 기존 더미 로직 재사용해도 됨
        //    (지금 요구사항 핵심이 STT 분석이면, 우선 면접 탭부터 실데이터로 만드는 게 우선순위)
        //    필요한 경우 기존 buildDummyEvaluationJson의 document/comparison/competency 부분만 잘라 붙여도 됨.
        root.set("documentAnalysis", buildDummyDocument());
        root.set("comparison", buildDummyComparison(overall));
        root.set("competency", buildDummyCompetency(overall));

        return root;
    }

// ======= 아래 유틸/더미 헬퍼들 =======

    private ObjectNode buildDummyDocument() {
        ObjectNode doc = objectMapper.createObjectNode();
        ObjectNode resume = doc.putObject("resume");
        resume.put("score", 80);
        resume.put("matchRate", 85);
        resume.putArray("keywords").add("React").add("Spring");
        resume.putArray("strengths").add("기술 스택 다양성");
        resume.putArray("improvements").add("성과 수치화 필요");

        ObjectNode cover = doc.putObject("coverLetter");
        cover.put("score", 78);
        cover.put("consistency", 82);
        cover.put("relevance", 76);
        cover.putArray("keywords").add("팀워크").add("성장");
        cover.putArray("improvements").add("지원동기 구체화");

        ObjectNode portfolio = doc.putObject("portfolio");
        portfolio.put("score", 83);
        portfolio.put("projectCount", 4);
        portfolio.put("technicalDepth", 80);
        portfolio.putArray("highlights").add("문서화");
        portfolio.putArray("improvements").add("테스트 보완");

        return doc;
    }

    private ObjectNode buildDummyComparison(int overall) {
        ObjectNode comp = objectMapper.createObjectNode();
        var hist = comp.putArray("scoreHistory");
        hist.addObject().put("date", "2023-12").put("score", clamp(overall - 8));
        hist.addObject().put("date", "2024-01").put("score", clamp(overall - 3));
        hist.addObject().put("date", "2024-02").put("score", clamp(overall));

        ObjectNode cat = comp.putObject("categoryComparison");
        cat.set("technical", triple(clamp(overall), 78, clamp(overall - 4)));
        cat.set("communication", triple(clamp(overall - 4), 80, clamp(overall - 6)));
        cat.set("problemSolving", triple(clamp(overall - 2), 79, clamp(overall - 5)));
        cat.set("attitude", triple(clamp(overall - 1), 82, clamp(overall - 2)));
        cat.set("experience", triple(clamp(overall - 5), 77, clamp(overall - 8)));

        comp.put("percentileRank", 60);
        return comp;
    }

    private ObjectNode buildDummyCompetency(int overall) {
        ObjectNode competency = objectMapper.createObjectNode();
        ObjectNode tech = competency.putObject("technical");
        tech.put("current", clamp(overall + 2));
        tech.put("target", 90);
        ObjectNode techD = tech.putObject("details");
        techD.put("frontEnd", clamp(overall + 4));
        techD.put("backEnd", clamp(overall - 6));
        techD.put("database", clamp(overall - 3));
        techD.put("deployment", clamp(overall - 2));

        ObjectNode soft = competency.putObject("soft");
        soft.put("current", clamp(overall - 2));
        soft.put("target", 85);
        ObjectNode softD = soft.putObject("details");
        softD.put("communication", clamp(overall - 1));
        softD.put("teamwork", clamp(overall + 1));
        softD.put("leadership", clamp(overall - 10));
        softD.put("presentation", clamp(overall - 5));

        var improvements = competency.putArray("improvements");
        improvements.addObject()
                .put("area", "답변 구조화")
                .put("priority", "high")
                .put("currentLevel", 60)
                .put("targetLevel", 85)
                .putArray("actionItems").add("STAR 기법").add("답변 템플릿");

        competency.putArray("recommendedResources")
                .addObject().put("type", "연습").put("title", "모의면접 3회").put("url", "#");

        return competency;
    }

    private int countWords(String s) {
        if (s == null || s.isBlank()) return 0;
        return s.trim().split("\\s+").length;
    }

    private int countFillers(String s) {
        if (s == null || s.isBlank()) return 0;
        // 아주 단순한 추임새 룰(필요하면 더 늘리면 됨)
        String[] fillers = {"음", "어", "그", "이제", "뭐", "약간", "사실"};
        int cnt = 0;
        for (String f : fillers) {
            cnt += countContains(s, f);
        }
        return cnt;
    }

    private int countFillersAll(List<InterviewTurn> turns) {
        int sum = 0;
        for (InterviewTurn t : turns) {
            String resp = firstNonBlank(t.getUserAnswerText(), t.getSttText(), "");
            sum += countFillers(resp);
        }
        return sum;
    }

    private int countContains(String s, String sub) {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
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
        return "45분";
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
}
