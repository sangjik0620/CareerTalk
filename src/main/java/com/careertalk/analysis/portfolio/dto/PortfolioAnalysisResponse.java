package com.careertalk.analysis.portfolio.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAnalysisResponse {

    private Long analysisId;
    private Long portfolioId;
    private String targetJob;
    private Integer overallScore;
    private String oneLineReview;
    private String summaryDetail;
    private String nickname;

    private List<ChartDataDto> chartData;

    private List<QuestionDto> questions;

    /*
      레이더 차트용 데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataDto {
        private String subject;  // 항목명 (예: 문제 해결력)
        private int score;       // 점수
        private int fullMark;    // 만점 기준 (보통 100)
    }

    /*
      예상 면접 질문 및 질문 의도
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private String q;        // 질문 내용
        private String intent;   // 질문 의도
    }
}
