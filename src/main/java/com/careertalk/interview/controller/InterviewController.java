package com.careertalk.interview.controller;

import com.careertalk.interview.dto.InterviewResultResponse;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.careertalk.interview.service.InterviewEvaluationService;
import com.careertalk.interview.service.InterviewResultService;
import com.careertalk.interview.service.InterviewService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewEvaluationService evaluationService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadInterview(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("questions") List<String> questions,
            @RequestParam("durationSec") int durationSec,
            @RequestParam("questionCount") int questionCount
    ) throws IOException {

        // TODO: 로그인 붙으면 SecurityContext/JWT에서 userId 가져오기
        Long userId = 1L;

        Long sessionId = interviewService.saveInterviewVoice(
                userId, files, questions, durationSec, questionCount
        );

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message", "업로드 성공"
        ));
    }

    @GetMapping("/sessions/{sessionId}/result")
    public ResponseEntity<?> result(@PathVariable Long sessionId) {
        JsonNode json = evaluationService.getEvaluationResultJsonOrNull(sessionId);
        if (json == null) {
            return ResponseEntity.status(202).body(java.util.Map.of(
                    "message", "analysis not ready",
                    "status", evaluationService.getAnalysisStatus(sessionId)
            ));
        }
        return ResponseEntity.ok(java.util.Map.of("evaluation", json));
    }

    @PostMapping("/sessions/{sessionId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable Long sessionId) {
        evaluationService.runAnalysis(sessionId); // 일단 동기로
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/sessions/{sessionId}/analysis/status")
    public ResponseEntity<?> analysisStatus(@PathVariable Long sessionId) {
        String status = evaluationService.getAnalysisStatus(sessionId);
        return ResponseEntity.ok(java.util.Map.of("status", status));
    }
}