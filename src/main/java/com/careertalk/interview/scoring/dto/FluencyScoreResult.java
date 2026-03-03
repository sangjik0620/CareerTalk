package com.careertalk.interview.scoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FluencyScoreResult {
    private int fluencyScore;            // 0~100
    private double analysisReliability;  // 0~1
    private Map<String, Double> components;
    private String version;              // "FL-1.0"
}