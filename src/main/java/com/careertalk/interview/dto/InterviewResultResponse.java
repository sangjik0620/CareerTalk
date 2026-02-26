package com.careertalk.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record InterviewResultResponse(
        InterviewSessionResultResponse voiceResult,
        JsonNode evaluation
) {}