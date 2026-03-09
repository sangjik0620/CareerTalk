package com.careertalk.interview.controller;

import com.careertalk.interview.dto.SessionTargetRequest;
import com.careertalk.interview.dto.InterviewResultV2Response;
import com.careertalk.interview.entity.AnalysisStatus;
import com.careertalk.interview.entity.InterviewEvaluation;
import com.careertalk.interview.repository.InterviewEvaluationRepository;
import com.careertalk.interview.service.InterviewAnalysisWorker;
import com.careertalk.interview.service.InterviewEvaluationService;
import com.careertalk.interview.service.InterviewResultService;
import com.careertalk.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewEvaluationService evaluationService;   // ✅ 여기 하나로 통일
    private final InterviewResultService interviewResultService;  // ✅ A안(V2) 결과 생성
    private final InterviewAnalysisWorker interviewAnalysisWorker;
    private final InterviewEvaluationRepository evaluationRepository;


    @PostMapping("/upload")
    public ResponseEntity<?> uploadInterview(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("questions") List<String> questions,
            @RequestParam("durationSec") int durationSec,
            @RequestParam("questionCount") int questionCount,
            @RequestParam(value = "targetsJson", required = false) String targetsJson
    ) throws IOException {

        Long userId = 1L;

        List<SessionTargetRequest> targets = java.util.Collections.emptyList();
        if (targetsJson != null && !targetsJson.isBlank()) {
            var om = new com.fasterxml.jackson.databind.ObjectMapper();
            var typeRef = new com.fasterxml.jackson.core.type.TypeReference<List<SessionTargetRequest>>() {};
            targets = om.readValue(targetsJson, typeRef);
        }

        Long sessionId = interviewService.saveInterviewVoice(
                userId, files, questions, durationSec, questionCount
        );

        interviewService.saveSessionTargets(sessionId, targets);

        return ResponseEntity.ok(Map.of("sessionId", sessionId, "message", "업로드 성공"));
    }

    @GetMapping("/sessions/{sessionId}/result")
    public ResponseEntity<?> result(@PathVariable Long sessionId) {

        String status = evaluationService.getAnalysisStatus(sessionId);
        log.info("status : {}", status);
        if (!"DONE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(202).body(Map.of(
                    "message", "analysis not ready",
                    "status", status,
                    "sessionId", sessionId
            ));
        }

        InterviewResultV2Response v2 = interviewResultService.getFullResultV2(sessionId);
        return ResponseEntity.ok(v2);
    }

    @PostMapping("/sessions/{sessionId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable Long sessionId) {
        System.out.println("[ANALYZE] request start sessionId=" + sessionId);

        try {
            evaluationRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        InterviewEvaluation eval = new InterviewEvaluation();
                        eval.setSessionId(sessionId);
                        eval.setOverallScore(0);
                        eval.setAnalysisStatus(AnalysisStatus.PENDING);
                        return evaluationRepository.save(eval);
                    });
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            System.out.println("[ANALYZE] evaluation row already exists sessionId=" + sessionId);
        }

        int updated = evaluationRepository.markProcessingIfPossible(sessionId);
        System.out.println("[ANALYZE] markProcessingIfPossible updated=" + updated);

        if (updated == 1) {
            System.out.println("[ANALYZE] worker start sessionId=" + sessionId);
            interviewAnalysisWorker.runAsync(sessionId);
            return ResponseEntity.accepted().body(Map.of(
                    "sessionId", sessionId,
                    "status", "PROCESSING"
            ));
        }

        String status = evaluationRepository.findAnalysisStatusBySessionId(sessionId);
        System.out.println("[ANALYZE] existing status=" + status);

        return ResponseEntity.ok().body(Map.of(
                "sessionId", sessionId,
                "status", status == null ? "PENDING" : status
        ));
    }

    @GetMapping("/sessions/{sessionId}/analysis/status")
    public ResponseEntity<?> analysisStatus(@PathVariable Long sessionId) {
        String status = evaluationService.getAnalysisStatus(sessionId);
        System.out.println("[STATUS API] sessionId=" + sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "status", status
        ));
    }
}