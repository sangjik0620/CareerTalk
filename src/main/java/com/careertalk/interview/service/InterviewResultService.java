package com.careertalk.interview.service;

import com.careertalk.interview.dto.InterviewResultResponse;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewResultService {
    private final InterviewService interviewService;                 // 기존 getVoiceResult() 가진 서비스
    private final InterviewEvaluationService evaluationService;      // 네가 만든 평가 서비스

    public InterviewResultResponse getFullResult(Long sessionId) {
        InterviewSessionResultResponse voice = interviewService.getVoiceResult(sessionId);
        JsonNode evaluation = evaluationService.getOrCreateEvaluationResultJson(sessionId);
        return new InterviewResultResponse(voice, evaluation);
    }
}
