package com.careertalk.analysis.coverletter.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CIAnalyzeResponse {

    private Long analysisId;
    private Long essayId;

    private String title;
    private String content;

    private Integer ruleScore;
    private Integer llmScore;
    private Integer totalScore;

    private String strengths;
    private String weaknesses;
    private String feedback;

    private List<QuestionItem> questions;

    private String updatedAt;

    private String jobRole;
    private String jobDetail;
}