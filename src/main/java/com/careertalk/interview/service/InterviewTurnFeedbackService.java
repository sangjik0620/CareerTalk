package com.careertalk.interview.service;

import com.careertalk.analysis.common.service.OpenAiService;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewTurnFeedbackService {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final InterviewTurnRepository turnRepository;

    /**
     * turn 1개에 대한 최종 feedback_json 생성 및 저장
     */
    @Transactional
    public String analyzeAndSaveTurnFeedback(
            InterviewTurn t,
            Integer overallVoiceScore,
            Integer confidenceScore,
            Integer fluencyScore,
            Integer tremorRiskScore,
            List<String> voiceFlags
    ) {
        String question = safe(t.getAiQuestion(), "");
        String answer = firstNonBlank(t.getUserAnswerText(), t.getSttText(), "");

        int ov = clamp100(nvl(overallVoiceScore));
        int cs = clamp100(nvl(confidenceScore));
        int fs = clamp100(nvl(fluencyScore));
        int ts = clamp100(nvl(tremorRiskScore));
        String voiceFlagsText = joinVoiceFlags(voiceFlags);

        // 답변 텍스트가 없으면 최소 스텁 저장
        if (answer.isBlank()) {
            ObjectNode stub = createBaseStub(
                    0,
                    "STT 텍스트가 없어 분석할 수 없습니다.",
                    "답변 텍스트가 없어 질문별 피드백을 생성하지 못했습니다.",
                    0,
                    ov, cs, fs, ts
            );

            String json = toString(stub);
            t.setFeedbackJson(json);
            turnRepository.save(t);
            return json;
        }

        try {
            String prompt = loadPrompt();

            String systemPrompt = prompt.substring(
                    prompt.indexOf("[SYSTEM]") + 8,
                    prompt.indexOf("[USER]")
            ).trim();

            String userTemplate = prompt.substring(
                    prompt.indexOf("[USER]") + 6
            ).trim();

            String userPrompt = userTemplate.formatted(
                    question,
                    answer,
                    ov,
                    cs,
                    fs,
                    ts,
                    voiceFlagsText
            );

            String resultJson = openAiService.getAiResponse(systemPrompt, userPrompt, null);
            JsonNode parsed = objectMapper.readTree(resultJson);
            ObjectNode normalized = normalizeTurnJson(parsed, ov, cs, fs, ts);

            String saved = toString(normalized);
            t.setFeedbackJson(saved);
            turnRepository.save(t);
            return saved;

        } catch (Exception e) {
            ObjectNode fallback = createBaseStub(
                    0,
                    "AI 분석 실패",
                    "AI 분석 중 오류가 발생했습니다: " + safe(e.getMessage(), "unknown"),
                    0,
                    ov, cs, fs, ts
            );

            String saved = toString(fallback);
            t.setFeedbackJson(saved);
            turnRepository.save(t);
            return saved;
        }
    }

    /**
     * LLM 응답 JSON을 새 feedback_json 스키마로 정규화
     */
    private ObjectNode normalizeTurnJson(
            JsonNode n,
            int overallVoiceScore,
            int confidenceScore,
            int fluencyScore,
            int tremorRiskScore
    ) {
        ObjectNode o = objectMapper.createObjectNode();

        o.put("score", clamp100(n.path("score").asInt(0)));
        o.put("oneLineFeedback", n.path("oneLineFeedback").asText("").trim());
        o.put("fullFeedback", n.path("fullFeedback").asText("").trim());
        o.put("sentimentScore", clamp100(n.path("sentimentScore").asInt(0)));

        ObjectNode kw = objectMapper.createObjectNode();
        kw.set("technical", arrayOrEmpty(n.path("keywords").path("technical")));
        kw.set("soft", arrayOrEmpty(n.path("keywords").path("soft")));
        kw.set("company", arrayOrEmpty(n.path("keywords").path("company")));
        o.set("keywords", kw);

        ObjectNode voice = objectMapper.createObjectNode();
        JsonNode inVoice = n.path("voice");

        // voice 점수는 프롬프트 입력값을 신뢰하고 고정 반영
        voice.put("overallVoiceScore", overallVoiceScore);
        voice.put("confidenceScore", confidenceScore);
        voice.put("fluencyScore", fluencyScore);
        voice.put("tremorRiskScore", tremorRiskScore);

        voice.set("strengths", limitStringArray(inVoice.path("strengths"), 2));
        voice.set("weaknesses", limitStringArray(inVoice.path("weaknesses"), 2));

        o.set("voice", voice);

        return o;
    }

    /**
     * 답변이 없거나 예외 발생 시 기본 구조 생성
     */
    private ObjectNode createBaseStub(
            int score,
            String oneLineFeedback,
            String fullFeedback,
            int sentimentScore,
            int overallVoiceScore,
            int confidenceScore,
            int fluencyScore,
            int tremorRiskScore
    ) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("score", clamp100(score));
        o.put("oneLineFeedback", safe(oneLineFeedback, ""));
        o.put("fullFeedback", safe(fullFeedback, ""));
        o.put("sentimentScore", clamp100(sentimentScore));

        ObjectNode keywords = objectMapper.createObjectNode();
        keywords.putArray("technical");
        keywords.putArray("soft");
        keywords.putArray("company");
        o.set("keywords", keywords);

        ObjectNode voice = objectMapper.createObjectNode();
        voice.put("overallVoiceScore", clamp100(overallVoiceScore));
        voice.put("confidenceScore", clamp100(confidenceScore));
        voice.put("fluencyScore", clamp100(fluencyScore));
        voice.put("tremorRiskScore", clamp100(tremorRiskScore));
        voice.putArray("strengths");
        voice.putArray("weaknesses");
        o.set("voice", voice);

        return o;
    }

    /**
     * 프롬프트 템플릿 로드
     * 경로: src/main/resources/prompts/interview_turn_feedback.txt
     */
    private String loadPrompt() {
        try {
            ClassPathResource resource =
                    new ClassPathResource("prompts/interview_turn_feedback.txt");

            return new String(resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("turn feedback prompt load failed", e);
        }
    }

    private JsonNode arrayOrEmpty(JsonNode n) {
        return (n != null && n.isArray()) ? n : objectMapper.createArrayNode();
    }

    private JsonNode limitStringArray(JsonNode n, int max) {
        var arr = objectMapper.createArrayNode();
        if (n != null && n.isArray()) {
            int count = 0;
            for (JsonNode item : n) {
                if (count >= max) break;
                String s = item.asText("").trim();
                if (!s.isBlank()) {
                    arr.add(s);
                    count++;
                }
            }
        }
        return arr;
    }

    private String joinVoiceFlags(List<String> voiceFlags) {
        if (voiceFlags == null || voiceFlags.isEmpty()) {
            return "없음";
        }
        return voiceFlags.stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private int clamp100(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
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