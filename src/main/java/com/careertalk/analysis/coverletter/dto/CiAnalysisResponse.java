package com.careertalk.analysis.coverletter.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CiAnalysisResponse {
    private long analysisId;

    private String jobRole;
    private String jobDetail;

    private String title;
    private String content;

    private int ruleScore;
    private int llmScore;
    private int totalScore;

    private String rewrittenEssay;
    private boolean rewriteGenerated;

    private String strengths;
    private String weaknesses;
    private String feedback;

    private List<QuestionItem> questions;

    private String updatedAt;
}