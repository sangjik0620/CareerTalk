package com.careertalk.interview.scoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OverallVoiceScoreResult {
    private int overallVoiceScore;          // 0~100
    private double overallReliability;      // 0~1
    private Map<String, Double> components; // confidence, stability, raw, applied
    private String grade;                   // EXCELLENT / GOOD / FAIR / NEEDS_WORK
    private String version;                 // "OV-1.0"
}