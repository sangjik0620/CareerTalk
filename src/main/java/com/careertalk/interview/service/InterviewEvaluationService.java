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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JsonNode getOrCreateEvaluationResultJson(Long sessionId) {
        String saved = evaluationRepository.findResultJsonBySessionId(sessionId);
        if (saved != null && !saved.isBlank()) {
            try {
                return objectMapper.readTree(saved);
            } catch (Exception ignored) {
                // 저장된 JSON이 깨졌을 때만 재생성
            }
        }

        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found"));
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        ObjectNode json = buildDummyEvaluationJson(session, turns);

        int overall = json.path("summary").path("overallScore").asInt(0);

        String jsonStr;
        try {
            jsonStr = objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("serialize failed", e);
        }

        // 파생 컬럼 동기화 (선택이지만 테이블에 있으니 같이 저장 추천)
        String strengths = joinArray(json.path("summary").path("strengths"));
        String weaknesses = joinArray(json.path("summary").path("weaknesses"));
        String nextActions = joinNextActions(json.path("summary").path("nextActions"));

        evaluationRepository.upsert(sessionId, overall, strengths, weaknesses, nextActions, jsonStr);
        return json;
    }

    private ObjectNode buildDummyEvaluationJson(InterviewSession session, List<InterviewTurn> turns) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        int overall = r.nextInt(62, 92);
        int prev = clamp(overall - r.nextInt(-6, 18));
        int passedAvg = clamp(r.nextInt(75, 86));
        int percentile = clamp(r.nextInt(30, 80));

        int totalQ = Math.max(turns.size(), 5);
        long answered = turns.stream().filter(t -> notBlank(t.getUserAnswerText()) || notBlank(t.getSttText())).count();
        int answeredQ = (int) Math.min(Math.max(answered == 0 ? totalQ - 1 : answered, 1), totalQ);

        ObjectNode root = objectMapper.createObjectNode();

        // interviewInfo
        ObjectNode interviewInfo = root.putObject("interviewInfo");
        interviewInfo.put("date", LocalDate.now().toString());
        interviewInfo.put("duration", formatDuration(session.getStartedAt(), session.getEndedAt()));
        interviewInfo.put("position", safe(session.getTitle(), "프론트엔드 개발자"));
        interviewInfo.put("company", "ABC Tech");

        // summary (프론트 스키마 1:1로 채우기)
        ObjectNode summary = root.putObject("summary");
        summary.put("overallScore", overall);
        summary.put("previousScore", prev);
        summary.put("passedAverage", passedAvg);

        summary.putArray("strengths").add("기술 역량").add("문제 해결 능력").add("커뮤니케이션");
        summary.putArray("weaknesses").add("답변 구조화").add("구체적 사례 부족").add("시간 관리");

        summary.put("totalQuestions", totalQ);
        summary.put("answeredQuestions", answeredQ);

        summary.put("verdict", pickVerdict(overall)); // "합격권" | "보류" | "개선필요"
        summary.put("percentileRank", percentile);

        summary.put("confidenceIndex", clamp(overall - r.nextInt(0, 10)));
        summary.put("jobFitIndex", clamp(overall - r.nextInt(0, 8)));
        summary.put("technicalIndex", clamp(overall + r.nextInt(0, 8)));
        summary.put("communicationIndex", clamp(overall - r.nextInt(0, 12)));

        summary.put("avgResponseTimeSec", round1(r.nextDouble(5.5, 12.5)));
        summary.put("fillerWordRate", round1(r.nextDouble(2.0, 10.0)));
        summary.put("sentimentScore", clamp(r.nextInt(55, 90)));

        summary.putArray("topKeywords")
                .add("React")
                .add("성능 최적화")
                .add("협업")
                .add("문제 해결")
                .add("사용자 관점");

        var next = summary.putArray("nextActions");
        next.addObject().put("title", "STAR 답변 템플릿 10문항 작성").put("dueDays", 3);
        next.addObject().put("title", "모의면접 3회 + 피드백 반영").put("dueDays", 7);
        next.addObject().put("title", "프로젝트 성과지표(수치) 5개 정리").put("dueDays", 5);

        // documentAnalysis
        ObjectNode doc = root.putObject("documentAnalysis");
        ObjectNode resume = doc.putObject("resume");
        resume.put("score", r.nextInt(70, 91));
        resume.put("matchRate", r.nextInt(72, 95));
        resume.putArray("keywords").add("React").add("TypeScript").add("Node.js").add("Git").add("Agile");
        resume.putArray("strengths").add("기술 스택 다양성").add("프로젝트 경험 풍부");
        resume.putArray("improvements").add("성과 수치화 필요").add("리더십 경험 추가");

        ObjectNode cover = doc.putObject("coverLetter");
        cover.put("score", r.nextInt(65, 86));
        cover.put("consistency", r.nextInt(70, 95));
        cover.put("relevance", r.nextInt(65, 92));
        cover.putArray("keywords").add("팀워크").add("혁신").add("성장").add("문제해결");
        cover.putArray("improvements").add("지원 동기 구체화").add("회사 분석 심화");

        ObjectNode portfolio = doc.putObject("portfolio");
        portfolio.put("score", r.nextInt(72, 96));
        portfolio.put("projectCount", r.nextInt(3, 8));
        portfolio.put("technicalDepth", r.nextInt(70, 95));
        portfolio.putArray("highlights").add("UI/UX 우수").add("코드 품질 높음").add("문서화 잘됨");
        portfolio.putArray("improvements").add("배포 경험 추가").add("테스트 케이스 보완");

        // interviewAnalysis
        ObjectNode interview = root.putObject("interviewAnalysis");
        ObjectNode voice = interview.putObject("voiceMetrics");
        voice.put("clarity", r.nextInt(60, 90));
        voice.put("pace", r.nextInt(55, 85));
        voice.put("volume", r.nextInt(60, 92));
        voice.put("confidence", r.nextInt(55, 88));
        voice.put("fillerWords", r.nextInt(4, 22));

        ObjectNode stt = interview.putObject("sttAnalysis");
        stt.put("totalWords", r.nextInt(1200, 3200));
        stt.put("averageResponseTime", round1(r.nextDouble(5.5, 12.5)));
        ObjectNode usage = stt.putObject("keywordUsage");
        usage.put("technical", r.nextInt(20, 60));
        usage.put("soft", r.nextInt(12, 45));
        usage.put("company", r.nextInt(5, 25));
        stt.put("sentimentScore", r.nextInt(55, 90));

        var qArr = interview.putArray("questionResponses");
        int limit = Math.min(Math.max(turns.size(), 3), 6);
        for (int i = 0; i < limit; i++) {
            InterviewTurn t = turns.isEmpty() ? null : turns.get(i);
            ObjectNode q = qArr.addObject();
            q.put("question", t != null ? safe(t.getAiQuestion(), "질문 " + (i + 1)) : "질문 " + (i + 1));
            String resp = (t != null) ? firstNonBlank(t.getUserAnswerText(), t.getSttText(), "더미 답변") : "더미 답변";
            q.put("response", resp);
            q.put("score", r.nextInt(60, 91));
            q.put("feedback", "핵심을 먼저 말하고, 사례를 1개 더 추가하면 좋아요.");
            q.put("duration", r.nextInt(60, 210));
        }

        // comparison
        ObjectNode comp = root.putObject("comparison");
        var hist = comp.putArray("scoreHistory");
        hist.addObject().put("date", "2023-11").put("score", clamp(prev - 10));
        hist.addObject().put("date", "2023-12").put("score", prev);
        hist.addObject().put("date", "2024-01").put("score", overall);

        ObjectNode cat = comp.putObject("categoryComparison");
        cat.set("technical", triple(clamp(overall - 1), r.nextInt(68, 82), clamp(prev - 2)));
        cat.set("communication", triple(clamp(overall - 6), r.nextInt(70, 86), clamp(prev - 5)));
        cat.set("problemSolving", triple(clamp(overall - 3), r.nextInt(68, 84), clamp(prev - 7)));
        cat.set("attitude", triple(clamp(overall - 2), r.nextInt(72, 88), clamp(prev - 3)));
        cat.set("experience", triple(clamp(overall - 7), r.nextInt(70, 86), clamp(prev - 9)));
        comp.put("percentileRank", percentile);

        // competency
        ObjectNode competency = root.putObject("competency");
        ObjectNode tech = competency.putObject("technical");
        tech.put("current", clamp(overall + 4));
        tech.put("target", 90);
        ObjectNode techD = tech.putObject("details");
        techD.put("frontEnd", clamp(overall + 7));
        techD.put("backEnd", clamp(overall - 8));
        techD.put("database", clamp(overall - 4));
        techD.put("deployment", clamp(overall - 1));

        ObjectNode soft = competency.putObject("soft");
        soft.put("current", clamp(overall - 3));
        soft.put("target", 85);
        ObjectNode softD = soft.putObject("details");
        softD.put("communication", clamp(overall - 2));
        softD.put("teamwork", clamp(overall + 1));
        softD.put("leadership", clamp(overall - 12));
        softD.put("presentation", clamp(overall - 6));

        var improvements = competency.putArray("improvements");
        improvements.addObject()
                .put("area", "답변 구조화")
                .put("priority", "high")
                .put("currentLevel", 60)
                .put("targetLevel", 85)
                .putArray("actionItems").add("STAR 기법 연습").add("답변 템플릿 작성").add("모의 면접 10회 이상");

        improvements.addObject()
                .put("area", "기술 심화 학습")
                .put("priority", "high")
                .put("currentLevel", 70)
                .put("targetLevel", 90)
                .putArray("actionItems").add("React 고급 패턴 학습").add("성능 최적화 경험").add("오픈소스 기여");

        improvements.addObject()
                .put("area", "비즈니스 이해도")
                .put("priority", "medium")
                .put("currentLevel", 65)
                .put("targetLevel", 80)
                .putArray("actionItems").add("산업 동향 분석").add("경쟁사 분석").add("비즈니스 모델 이해");

        var resources = competency.putArray("recommendedResources");
        resources.addObject().put("type", "강의").put("title", "React 고급 패턴").put("url", "#");
        resources.addObject().put("type", "도서").put("title", "면접의 기술").put("url", "#");
        resources.addObject().put("type", "연습").put("title", "모의 면접 플랫폼").put("url", "#");

        return root;
    }

    private ObjectNode triple(int user, int avg, int prev) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("user", clamp(user));
        n.put("average", clamp(avg));
        n.put("previous", clamp(prev));
        return n;
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
}