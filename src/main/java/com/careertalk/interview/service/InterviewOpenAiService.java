package com.careertalk.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewOpenAiService {

    private final ObjectMapper objectMapper;

    /**
     * 네가 가진 설정:
     * openai.api-key=${INTERVIEW_AI_API_KEY}
     *
     * 기존 파트(CI)는 ai.api-key/ai.api-url을 쓰므로,
     * 둘 중 뭐가 있어도 동작하게 fallback을 걸어둠.
     */
    @Value("${openai.api-key:${ai.api-key:}}")
    private String apiKey;

    /**
     * CI 파트는 ai.api-url을 사용. :contentReference[oaicite:3]{index=3}
     * 인터뷰는 openai.api-url로 따로 줄 수도 있으니 fallback.
     * 기본값은 OpenAI Responses API endpoint.
     */
    @Value("${openai.api-url:${ai.api-url:https://api.openai.com/v1/responses}}")
    private String apiUrl;

    @Value("${openai.model:gpt-4.1-mini}")
    private String model;

    /**
     * "반드시 JSON만 출력"하도록 강하게 유도한 프롬프트를 넣고,
     * 모델이 반환한 텍스트를 JSON으로 파싱해서 돌려준다.
     */
    public JsonNode callJsonOnly(String systemPrompt, String userPrompt) throws IOException {
        String prompt = "SYSTEM:\n" + safe(systemPrompt) + "\n\nUSER:\n" + safe(userPrompt);

        JsonNode raw = callOpenAiRaw(prompt);

        // callOpenAiRaw는 모델 output 텍스트를 꺼내왔고,
        // 그 텍스트가 JSON 문자열이라고 가정하고 다시 parse 해서 리턴함.
        return raw;
    }

    /**
     * CI 파트와 동일한 방식:
     * - RestTemplate POST
     * - root.output[0].content[0].text 추출
     * - 그 text를 JSON으로 다시 parse
     *
     * (CI의 callOpenAiRaw 패턴 그대로) :contentReference[oaicite:4]{index=4}
     */
    private JsonNode callOpenAiRaw(String prompt) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("openai.api-key (or ai.api-key)가 설정되지 않았습니다.");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("openai.api-url (or ai.api-url)가 설정되지 않았습니다.");
        }

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("OpenAI 응답이 비어있습니다.");
        }

        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error") && !root.get("error").isNull()) {
            throw new IllegalStateException("OpenAI ERROR: " + root.get("error").toString());
        }

        JsonNode outputNode = root.path("output");
        if (!outputNode.isArray() || outputNode.isEmpty()) {
            throw new IllegalStateException("OpenAI 응답 구조 이상 (output 없음)");
        }

        JsonNode contentNode = outputNode.get(0).path("content");
        if (!contentNode.isArray() || contentNode.isEmpty()) {
            throw new IllegalStateException("OpenAI 응답 구조 이상 (content 없음)");
        }

        String outputText = contentNode.get(0).path("text").asText();
        if (outputText == null || outputText.isBlank()) {
            throw new IllegalStateException("GPT 응답 텍스트가 비어있습니다.");
        }

        // 모델이 JSON만 출력해야 하므로 JSON parse
        return objectMapper.readTree(outputText.trim());
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}