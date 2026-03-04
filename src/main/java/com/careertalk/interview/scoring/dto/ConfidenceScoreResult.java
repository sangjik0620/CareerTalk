package com.careertalk.interview.scoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfidenceScoreResult {

    private int confidenceScore;          // 0~100
    private double analysisReliability;   // 0~1

    private Map<String, Double> components;  // 내부 점수 구성요소

    private List<FlagItem> flags;

    private String version;
}