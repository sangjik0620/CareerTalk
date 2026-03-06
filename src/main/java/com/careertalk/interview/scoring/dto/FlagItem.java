package com.careertalk.interview.scoring.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FlagItem {
    private String code;     // e.g. JITTER_HIGH
    private String message;  // 사용자 피드백 문장
}