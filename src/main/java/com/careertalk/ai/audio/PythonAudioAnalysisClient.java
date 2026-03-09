package com.careertalk.ai.audio;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.Exceptions;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Python(FastAPI) 음성 분석 서비스 호출 클라이언트
 * - POST {baseUrl}/analyze-audio (multipart file 업로드)
 * - return: JSON(JsonNode)
 */
@Service
@RequiredArgsConstructor
public class PythonAudioAnalysisClient {

    private final WebClient webClient = WebClient.builder().build();

    @Value("${audio.analysis.base-url:http://localhost:8001}")
    private String baseUrl;

    /**
     * @param wavPath STT 후 변환된 WAV 파일 경로
     * @return Python 서비스 분석 결과 JSON
     */


    public JsonNode analyzeWav(Path wavPath) {
        FileSystemResource fileResource = new FileSystemResource(wavPath.toFile());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", fileResource);

        try {
            long start = System.currentTimeMillis();
            System.out.println("[PythonAudioAnalysisClient] request start: " + wavPath);

            String raw = webClient.post()
                    .uri(baseUrl + "/analyze-audio")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMinutes(3))
                    .block();

            long end = System.currentTimeMillis();
            System.out.println("[PythonAudioAnalysisClient] response received: " + ((end - start) / 1000.0) + "s");

            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);

        } catch (WebClientResponseException e) {
            System.out.println("[PythonAudioAnalysisClient] HTTP error: " + e.getStatusCode()
                    + " body=" + e.getResponseBodyAsString());
            throw new IllegalStateException("Python audio analysis failed: "
                    + e.getStatusCode() + " body=" + e.getResponseBodyAsString(), e);

        } catch (Exception e) {
            Throwable root = Exceptions.unwrap(e);
            System.out.println("[PythonAudioAnalysisClient] exception: " + root);
            if (root instanceof TimeoutException) {
                throw new IllegalStateException("Python audio analysis timeout after 3 minutes: " + wavPath, e);
            }
            throw new IllegalStateException("Python audio analysis error: " + e.getMessage(), e);
        }
    }
}