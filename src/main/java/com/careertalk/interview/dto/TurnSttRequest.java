package com.careertalk.interview.dto;

import lombok.Data;

@Data
public class TurnSttRequest {
    private boolean toWav = true;     // 안정성 우선
    private boolean force = false;    // SUCCESS여도 다시 돌릴지
}