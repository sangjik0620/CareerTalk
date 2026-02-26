package com.careertalk.analysis.commonservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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


    public String getAiResponse(String systemPrompt, String userPrompt) {

        // 1. HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey); // 인증키 설정

        // 2. 요청 바디 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o"); // 이미지 분석까지 가능한 최신 모델
        requestBody.put("response_format", Map.of("type", "json_object")); // ⭐ JSON 응답 강제

        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("OpenAI API 호출 시작...");

            // 3. API 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                    OPENAI_URL, HttpMethod.POST, entity, Map.class
            );

            // 4. 응답 데이터 파싱
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return null;

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

            // ⭐ 이것이 바로 우리가 기다리던 aiResultJson 입니다!
            String aiResultJson = (String) message.get("content");

            log.info("OpenAI API 호출 성공!");
            return aiResultJson;

        } catch (Exception e) {
            log.error("OpenAI API 호출 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("AI 분석 서비스 통신 실패", e);
        }
    }
}
