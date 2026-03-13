package com.careertalk.interview.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionTargetRequest {
    private String targetType;
    private Long targetId;
    private Long analysisId;
}
