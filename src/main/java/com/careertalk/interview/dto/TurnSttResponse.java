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

    // ✅ 다음 화면 이동용(프론트가 navigate)
    private String nextPath;
}