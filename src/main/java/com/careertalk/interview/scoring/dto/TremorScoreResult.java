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
    private int tremorRiskScore;
    private double analysisReliability;
    private Map<String, Double> riskComponents;
    private List<FlagItem> flags;
    private String version;
}