package com.careertalk.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewOpenAiService {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.model.name}")
    private String model;

    /**
     * JSON-only 결과가 필요할 때 사용.
     * - 모델 출력 텍스트를 JSON 파싱
     * - 파싱 실패하면 {error, raw}를 리턴 (서버 템플릿 대체 금지)
     */
    public JsonNode callJsonOnly(String systemPrompt, String userPrompt) {
        String text = callText(systemPrompt, userPrompt);

        String cleaned = normalizeModelText(text);

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("[LLM] JSON parse failed. model={}, rawPreview={}",
                    model, shorten(cleaned, 300));
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", "LLM returned non-JSON or invalid JSON");
            err.put("raw", cleaned);
            return err;
        }
    }

    /**
     * Responses API 호출 -> 모델 출력 텍스트 반환
     * - input을 메시지 배열 형태로 전송
     * - output_text 우선, 없으면 output[].content[].text 합침
     */
    public String callText(String systemPrompt, String userPrompt) {
        ensureConfig();

        RestTemplate rt = new RestTemplate();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        ArrayNode input = body.putArray("input");
        input.add(message("system", systemPrompt));
        input.add(message("user", userPrompt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("serialize request failed", e);
        }

        // 호출 여부/중복 여부 확인용
        log.info("[LLM] OpenAI request start. model={}, url={}, payloadBytes={}",
                model, apiUrl, reqJson.getBytes(StandardCharsets.UTF_8).length);

        ResponseEntity<String> res;
        try {
            res = rt.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(reqJson, headers),
                    String.class
            );
        } catch (RestClientException e) {
            log.error("[LLM] OpenAI HTTP call failed. model={}, url={}, err={}",
                    model, apiUrl, e.toString());
            throw new IllegalStateException("OpenAI HTTP call failed: " + e.getMessage(), e);
        }

        String resBody = res.getBody();
        if (resBody == null || resBody.isBlank()) {
            throw new IllegalStateException("OpenAI response body is empty");
        }

        try {
            JsonNode root = objectMapper.readTree(resBody);

            if (root.has("error") && !root.get("error").isNull()) {
                throw new IllegalStateException("OpenAI error: " + root.get("error").toString());
            }

            String outputText = root.path("output_text").asText(null);
            if (outputText != null && !outputText.isBlank()) {
                log.info("[LLM] OpenAI response ok. model={}, outputLen={}", model, outputText.length());
                return outputText;
            }

            String merged = extractFromOutput(root);
            if (merged != null && !merged.isBlank()) {
                log.info("[LLM] OpenAI response ok(merged). model={}, outputLen={}", model, merged.length());
                return merged;
            }

            // 그래도 없으면 전체 JSON 반환 (디버깅)
            log.warn("[LLM] OpenAI response has no output_text/content. model={}, bodyPreview={}",
                    model, shorten(resBody, 300));
            return root.toString();

        } catch (Exception e) {
            log.error("[LLM] parse OpenAI response failed. model={}, bodyPreview={}",
                    model, shorten(resBody, 300));
            throw new IllegalStateException("parse OpenAI response failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private ObjectNode message(String role, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);

        ArrayNode content = msg.putArray("content");
        ObjectNode c = content.addObject();
        c.put("type", "input_text");
        c.put("text", text == null ? "" : text);

        return msg;
    }

    private String extractFromOutput(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) return null;

        StringBuilder sb = new StringBuilder();

        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;

            for (JsonNode c : content) {
                String t = c.path("text").asText("");
                if (t != null && !t.isBlank()) sb.append(t);
            }
        }

        String merged = sb.toString().trim();
        return merged.isBlank() ? null : merged;
    }

    private String normalizeModelText(String text) {
        if (text == null) return "";

        String t = text.trim();

        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                t = t.substring(firstNewline + 1, lastFence).trim();
            }
        }

        t = t.replace("\uFEFF", "").trim();
        return t;
    }

    private void ensureConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI apiKey is blank. (openai.api-key or ai.api-key not set)");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("OpenAI apiUrl is blank. (openai.api-url or ai.api-url not set)");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("OpenAI model is blank. (openai.model or ai.model.name not set)");
        }
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }
}