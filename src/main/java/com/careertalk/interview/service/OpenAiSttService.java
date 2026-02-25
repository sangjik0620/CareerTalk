package com.careertalk.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OpenAiSttService {

    private final WebClient webClient;

    public OpenAiSttService(@Value("${openai.api-key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }

    public String transcribe(Path audioPath) throws Exception {
        byte[] bytes = Files.readAllBytes(audioPath);
        String filename = audioPath.getFileName().toString();

        // 파일 파트 (파일명 반드시 넣어주는 게 중요)
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("model", "whisper-1"); // 또는 gpt-4o-mini-transcribe 등 :contentReference[oaicite:2]{index=2}
        formData.add("file", fileResource);
        // 옵션: formData.add("language", "ko"); (선택)
        // 옵션: formData.add("response_format", "json"); (whisper-1은 json/text/srt/vtt 등 지원) :contentReference[oaicite:3]{index=3}

        // 응답은 기본적으로 { "text": "..." } 형태(json)로 옴
        return webClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
