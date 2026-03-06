package com.careertalk.interview.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionTargetRequest {
    private String targetType; // "RESUME" | "ESSAY" | "PORTFOLIO"
    private Long targetId;
    private Long analysisId;
}
