package com.careertalk.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewEvaluationResultResponse {
    private InterviewInfo interviewInfo;
    private Summary summary;
    private DocumentAnalysis documentAnalysis;
    private InterviewAnalysis interviewAnalysis;
    private Comparison comparison;
    private Competency competency;
    private JsonNode evaluation;   // 혹은 resultJson

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewInfo {
        private String date;
        private String duration;
        private String title;
        private String company;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private Integer overallScore;
        private Integer previousScore;
        private Integer passedAverage;

        private List<String> strengths;
        private List<String> weaknesses;

        private Integer totalQuestions;
        private Integer answeredQuestions;

        private String verdict;
        private Integer percentileRank;

        private Integer confidenceIndex;
        private Integer jobFitIndex;
        private Integer technicalIndex;
        private Integer communicationIndex;

        private Double avgResponseTimeSec;
        private Double fillerWordRate;
        private Integer sentimentScore;

        private List<String> topKeywords;
        private List<NextAction> nextActions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NextAction {
        private String title;
        private Integer dueDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentAnalysis {
        private Resume resume;
        private CoverLetter coverLetter;
        private Portfolio portfolio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Resume {
        private Integer score;
        private List<String> keywords;
        private Integer matchRate;
        private List<String> strengths;
        private List<String> improvements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CoverLetter {
        private Integer score;
        private Integer consistency;
        private Integer relevance;
        private List<String> keywords;
        private List<String> improvements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Portfolio {
        private Integer score;
        private Integer projectCount;
        private Integer technicalDepth;
        private List<String> highlights;
        private List<String> improvements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewAnalysis {
        private VoiceMetrics voiceMetrics;
        private SttAnalysis sttAnalysis;
        private List<QuestionResponse> questionResponses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoiceMetrics {
        private Integer clarity;
        private Integer pace;
        private Integer volume;
        private Integer confidence;
        private Integer fillerWords;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SttAnalysis {
        private Integer totalWords;
        private Double averageResponseTime;
        private Map<String, Integer> keywordUsage; // technical/soft/company
        private Integer sentimentScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResponse {
        private String question;
        private String response;
        private Integer score;
        private String feedback;
        private Integer duration;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Comparison {
        private List<ScorePoint> scoreHistory;
        private Map<String, CategoryCompare> categoryComparison; // technical/communication/...
        private Integer percentileRank;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScorePoint {
        private String date;
        private Integer score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryCompare {
        private Integer user;
        private Integer average;
        private Integer previous;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Competency {
        private CompetencyBlock technical;
        private CompetencyBlock soft;
        private List<Improvement> improvements;
        private List<Resource> recommendedResources;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompetencyBlock {
        private Integer current;
        private Integer target;
        private Map<String, Integer> details;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Improvement {
        private String area;
        private String priority; // high/medium
        private Integer currentLevel;
        private Integer targetLevel;
        private List<String> actionItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Resource {
        private String type;
        private String title;
        private String url;
    }
}
