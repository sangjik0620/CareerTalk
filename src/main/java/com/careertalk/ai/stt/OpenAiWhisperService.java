package com.careertalk.ai.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class OpenAiWhisperService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient openai = WebClient.builder()
            .baseUrl("https://api.openai.com")
            .build();

    @Value("${openai.api-key}")
    private String apiKey;

    /**
     * @return transcript text
     */
    public String transcribe(Path audioFilePath) throws Exception {
        FileSystemResource fileResource = new FileSystemResource(audioFilePath.toFile());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", "whisper-1");
        form.add("file", fileResource);
        form.add("language", "ko"); // 선택이지만 한국어면 넣는 게 보통 도움됨

        String raw = openai.post()
                .uri("/v1/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = objectMapper.readTree(raw);
        return node.path("text").asText("");
    }
}
