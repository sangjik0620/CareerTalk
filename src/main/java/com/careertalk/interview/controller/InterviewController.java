package com.careertalk.interview.controller;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.service.MemberService;
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
import org.springframework.http.HttpStatus;
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
    private final InterviewEvaluationService evaluationService;
    private final InterviewResultService interviewResultService;
    private final InterviewAnalysisWorker interviewAnalysisWorker;
    private final InterviewEvaluationRepository evaluationRepository;

    private final JwtUtil jwtUtil;
    private final MemberService memberService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadInterview(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("questions") List<String> questions,
            @RequestParam("durationSec") int durationSec,
            @RequestParam("questionCount") int questionCount,
            @RequestParam(value = "targetsJson", required = false) String targetsJson
    ) throws IOException {

        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        String jwtToken = token.substring(7);
        String loginId;

        try {
            loginId = jwtUtil.getLoginId(jwtToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "유효하지 않은 토큰입니다."));
        }

        Member member = memberService.findByLoginId(loginId);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }

        Long userNum = member.getUserNum();

        List<SessionTargetRequest> targets = java.util.Collections.emptyList();
        if (targetsJson != null && !targetsJson.isBlank()) {
            var om = new com.fasterxml.jackson.databind.ObjectMapper();
            var typeRef = new com.fasterxml.jackson.core.type.TypeReference<List<SessionTargetRequest>>() {};
            targets = om.readValue(targetsJson, typeRef);
        }

        Long sessionId = interviewService.saveInterviewVoice(
                userNum, files, questions, durationSec, questionCount
        );

        interviewService.saveSessionTargets(sessionId, targets);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "userNum", userNum,
                "message", "업로드 성공"
        ));
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
        evaluationRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    InterviewEvaluation eval = new InterviewEvaluation();
                    eval.setSessionId(sessionId);
                    eval.setOverallScore(0);
                    eval.setAnalysisStatus(AnalysisStatus.PENDING);
                    return evaluationRepository.save(eval);
                });

        int updated = evaluationRepository.markProcessingIfPossible(sessionId);

        if (updated == 1) {
            interviewAnalysisWorker.runAsync(sessionId);

            return ResponseEntity.accepted().body(Map.of(
                    "sessionId", sessionId,
                    "status", "PROCESSING"
            ));
        }

        String status = evaluationRepository.findAnalysisStatusBySessionId(sessionId);

        return ResponseEntity.ok().body(Map.of(
                "sessionId", sessionId,
                "status", status == null ? "PENDING" : status
        ));
    }

    @GetMapping("/sessions/{sessionId}/analysis/status")
    public ResponseEntity<?> analysisStatus(@PathVariable Long sessionId) {
        String status = evaluationService.getAnalysisStatus(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "status", status
        ));
    }
}