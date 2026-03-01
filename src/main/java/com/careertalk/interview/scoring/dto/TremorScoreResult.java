package com.careertalk.interview.scoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TremorScoreResult {
    private int tremorRiskScore;                 // 0~100
    private double analysisReliability;          // 0~1
    private Map<String, Double> riskComponents;  // rJitter, rShimmer, rPitchCv, rSilence, weightedRisk01
    private List<FlagItem> flags;                // 설명용
    private String version;                      // "TR-1.0"
}