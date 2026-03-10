package com.careertalk.analysis.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisHistoryResponse {

    private List<Item> resume;
    private List<Item> coverLetter;
    private List<Item> portfolio;
    private List<String> expectedQuestions;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;          // 프론트 선택용
        private Long targetId;    // resumes.resume_id / essays.essay_id / portfolios.portfolio_id
        private Long analysisId;  // analysis.analysis_id
        private String title;
        private String fileName;
        private String analyzedAt;
        private Integer score;
        private List<String> keywords;
        private List<String> expectedQuestions;
    }
}