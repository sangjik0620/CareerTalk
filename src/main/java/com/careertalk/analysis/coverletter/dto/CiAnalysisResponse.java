package com.careertalk.analysis.coverletter.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CiAnalysisResponse {

    private Long analysisId;

    private String title;
    private String content;

    private Integer ruleScore;
    private Integer llmScore;
    private Integer totalScore;

    private String strengths;
    private String weaknesses;
    private String feedback;

    private List<String> questions;
    private List<String> questionIntents;

    private String updatedAt;
}