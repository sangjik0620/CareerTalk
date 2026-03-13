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
    private final TurnSttService turnSttService;

    @Async("analysisExecutor")
    public void runAsync(Long sessionId) {
        log.info("[ANALYSIS] worker start sessionId={} thread={}",
                sessionId,
                Thread.currentThread().getName());

        try {
            log.info("[ANALYSIS] turn pipeline start sessionId={}", sessionId);
            turnSttService.processSessionTurns(sessionId);
            log.info("[ANALYSIS] turn pipeline done sessionId={}", sessionId);

            log.info("[ANALYSIS] LLM analysis start sessionId={}", sessionId);
            interviewEvaluationService.runAnalysisInternal(sessionId);
            log.info("[ANALYSIS] LLM analysis done sessionId={}", sessionId);

            interviewEvaluationService.markDone(sessionId);
            log.info("[ANALYSIS] worker done sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("[ANALYSIS] worker failed sessionId={}", sessionId, e);
            interviewEvaluationService.markFailed(sessionId, e.getMessage());
        }
    }
}