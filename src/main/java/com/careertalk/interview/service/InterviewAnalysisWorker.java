package com.careertalk.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnalysisWorker {

    private final InterviewEvaluationService interviewEvaluationService;

    // ✅ AsyncConfig에서 만든 executor 이름과 동일해야 함
    @Async("analysisExecutor")
    public void runAsync(Long sessionId) {
//        log.info("===== ASYNC ANALYSIS START sessionId={} thread={} =====",
//                sessionId,
//                Thread.currentThread().getName());
        try {
            // 내부 분석 실행 (기존 runAnalysis를 runAnalysisInternal로 바꿔서 호출할 예정)
            interviewEvaluationService.runAnalysisInternal(sessionId);

            // 성공 상태는 내부에서 처리하거나 여기서 처리(둘 중 하나로 통일)
            interviewEvaluationService.markDone(sessionId);
        } catch (Exception e) {
            log.error("Interview analysis failed. sessionId={}", sessionId, e);
            interviewEvaluationService.markFailed(sessionId, e.getMessage());
        }
    }
}