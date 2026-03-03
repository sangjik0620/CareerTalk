package com.careertalk.analysis.commonservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenAiService {

    @Value("${ai.api-key}")
    private String apiKey;

    private final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();

    //  base64Images 파라미터 추가
    public String getAiResponse(String systemPrompt, String userPrompt, List<String> base64Images) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4.1-nano");
        requestBody.put("response_format", Map.of("type", "json_object"));

        // 텍스트와 이미지를 하나의 리스트로 조립
        List<Map<String, Object>> contentList = new ArrayList<>();
        contentList.add(Map.of("type", "text", "text", userPrompt));

        if (base64Images != null && !base64Images.isEmpty()) {
            for (String base64 : base64Images) {
                //  핵심: detail: low
                contentList.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of(
                                "url", "data:image/png;base64," + base64,
                                "detail", "low"
                        )
                ));
            }
        }

        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", contentList) // 변경됨
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("OpenAI API 호출 시작... (이미지 {}장 포함)", base64Images != null ? base64Images.size() : 0);

            ResponseEntity<Map> response = restTemplate.exchange(
                    OPENAI_URL, HttpMethod.POST, entity, Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return null;

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

            String aiResultJson = (String) message.get("content");

            log.info("OpenAI API 호출 성공!");
            return aiResultJson;

        } catch (Exception e) {
            log.error("OpenAI API 호출 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("AI 분석 서비스 통신 실패", e);
        }
    }
}