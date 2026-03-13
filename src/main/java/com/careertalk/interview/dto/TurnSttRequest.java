package com.careertalk.interview.dto;

import lombok.Data;

@Data
public class TurnSttRequest {
    private boolean toWav = true;
    private boolean force = false;
}