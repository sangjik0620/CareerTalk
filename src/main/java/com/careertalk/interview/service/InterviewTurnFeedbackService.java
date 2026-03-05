package com.careertalk.interview.service;

import com.careertalk.analysis.common.service.OpenAiService;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewTurnFeedbackService {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final InterviewTurnRepository turnRepository;

    /**
     * STT 텍스트 기반으로 turn별 피드백 JSON 생성 → interview_turns.feedback_json 저장
     */
    @Transactional
    public String analyzeAndSaveTurnFeedback(InterviewTurn t) {
        String question = safe(t.getAiQuestion(), "");
        String answer = firstNonBlank(t.getUserAnswerText(), t.getSttText(), "");

        // STT가 없으면 분석 불가 → 최소 스텁 저장
        if (answer.isBlank()) {
            ObjectNode stub = objectMapper.createObjectNode();
            stub.put("score", 0);
            stub.put("feedback", "STT 텍스트가 없어 분석할 수 없습니다.");
            stub.put("sentimentScore", 0);

            // keywords
            ObjectNode keywords = objectMapper.createObjectNode();
            keywords.putArray("technical");
            keywords.putArray("soft");
            keywords.putArray("company");
            stub.set("keywords", keywords);

            // logic
            ObjectNode logic = objectMapper.createObjectNode();
            logic.put("score", 0);
            logic.putArray("missing");
            logic.put("feedback", "답변 텍스트가 필요합니다.");
            logic.put("rewriteExample", "");
            stub.set("logic", logic);

            // penalty
            stub.putArray("penalty");
            String json = toString(stub);
            t.setFeedbackJson(json);
            turnRepository.save(t);
            return json;
        }

        String systemPrompt = """
        당신은 면접관 겸 면접 코치입니다.
        사용자의 면접 질문/답변(STT 텍스트)을 분석해 개선 가능한 피드백을 JSON으로만 출력하세요.
        출력은 반드시 JSON 객체 하나여야 하며, 다른 텍스트는 절대 포함하지 마세요.
        """;

        String userPrompt = """
        아래 '질문'과 '답변(STT)'을 분석하여 다음 스키마로 JSON을 생성하세요.
        
        [스키마]
        {
          "score": 0~100,
          "feedback": "한 줄 요약 피드백",
          "sentimentScore": 0~100,
          "keywords": {
            "technical": ["..."],
            "soft": ["..."],
            "company": ["..."]
          },
          "logic": {
            "score": 0~100,
            "missing": ["도입","근거","결론"] 중 누락된 것,
            "feedback": "논리/구조 피드백",
            "rewriteExample": "개선된 답변 예시(짧게)"
          },
          "penalty": [
            {
              "type": "VAGUE|REPEAT|EVADE",
              "phrase": "문제 표현",
              "reason": "왜 감점인지",
              "suggestion": "개선 표현 예시"
            }
          ]
        }
        
        [질문]
        %s
        
        [답변(STT)]
        %s
        """.formatted(question, answer);

        try {
            String resultJson = openAiService.getAiResponse(systemPrompt, userPrompt, null);
            // JSON 유효성 체크
            JsonNode parsed = objectMapper.readTree(resultJson);
            // 최소 필드 보정(없으면 기본값)
            ObjectNode normalized = normalizeTurnJson(parsed);
            String saved = toString(normalized);

            t.setFeedbackJson(saved);
            turnRepository.save(t);
            return saved;

        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("score", 0);
            fallback.put("feedback", "AI 분석 실패: " + e.getMessage());
            fallback.put("sentimentScore", 0);

            // keywords
            ObjectNode keywords = objectMapper.createObjectNode();
            keywords.putArray("technical");
            keywords.putArray("soft");
            keywords.putArray("company");
            fallback.set("keywords", keywords);

            // logic
            ObjectNode logic = objectMapper.createObjectNode();
            logic.put("score", 0);
            logic.putArray("missing");
            logic.put("feedback", "분석 실패");
            logic.put("rewriteExample", "");
            fallback.set("logic", logic);

            // penalty
            fallback.putArray("penalty");

            String saved = toString(fallback);
            t.setFeedbackJson(saved);
            turnRepository.save(t);
            return saved;
        }
    }

    private ObjectNode normalizeTurnJson(JsonNode n) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("score", clamp100(n.path("score").asInt(0)));
        o.put("feedback", n.path("feedback").asText(""));

        o.put("sentimentScore", clamp100(n.path("sentimentScore").asInt(0)));

        // keywords
        ObjectNode kw = objectMapper.createObjectNode();
        kw.set("technical", arrayOrEmpty(n.path("keywords").path("technical")));
        kw.set("soft", arrayOrEmpty(n.path("keywords").path("soft")));
        kw.set("company", arrayOrEmpty(n.path("keywords").path("company")));
        o.set("keywords", kw);

        // logic
        ObjectNode logic = objectMapper.createObjectNode();
        logic.put("score", clamp100(n.path("logic").path("score").asInt(0)));
        logic.set("missing", arrayOrEmpty(n.path("logic").path("missing")));
        logic.put("feedback", n.path("logic").path("feedback").asText(""));
        logic.put("rewriteExample", n.path("logic").path("rewriteExample").asText(""));
        o.set("logic", logic);

        // penalty
        o.set("penalty", arrayOrEmpty(n.path("penalty")));

        return o;
    }

    private JsonNode arrayOrEmpty(JsonNode n) {
        return (n != null && n.isArray()) ? n : objectMapper.createArrayNode();
    }

    private int clamp100(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private String toString(JsonNode n) {
        try {
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException("serialize failed", e);
        }
    }

    private String safe(String s, String fb) {
        return (s == null || s.isBlank()) ? fb : s;
    }

    private String firstNonBlank(String a, String b, String fb) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fb;
    }
}