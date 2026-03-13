package com.careertalk.interview.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TurnSttResponse {
    private Long turnId;
    private String sttStatus;
    private Integer attemptCount;
    private String sttText;
    private String errorMessage;
    private String nextPath;
}