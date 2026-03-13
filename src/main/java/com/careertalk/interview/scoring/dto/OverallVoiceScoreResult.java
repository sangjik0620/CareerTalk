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
    private int overallVoiceScore;
    private double overallReliability;
    private Map<String, Double> components;
    private String grade;
    private String version;
}