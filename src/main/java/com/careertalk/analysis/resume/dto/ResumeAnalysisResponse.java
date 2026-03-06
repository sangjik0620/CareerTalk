package com.careertalk.analysis.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysisResponse {

    private Long analysisId;
    private Long resumeId;
    private String targetJob;           // 지원 직군 (예: IT개발∙데이터)
    private String detailedPosition;    // 상세 포지션 (예: 백엔드 개발자 3년차)
    private Integer overallScore;       // 총점 (100점 만점)
    private String summaryDetail;       // 종합 총평

    private DetailedEvaluationDto detailedEvaluation;   // 5개 항목별 점수 + 평가

    private List<String> strengths;     // 강점
    private List<String> weaknesses;    // 약점
    private List<String> improvements;  // 개선방안

    private List<QuestionDto> expectedQuestionsJson;    // 예상 면접 질문

    private String createdAt;           // 분석 일시

    /* ─────────────────────────────────────────
       5개 항목별 상세 평가
       프롬프트 키: jobFitScore, experienceScore,
                   skillScore, growthScore, completenessScore
    ───────────────────────────────────────── */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailedEvaluationDto {
        private EvalItemDto jobFitScore;        // 직무 적합성
        private EvalItemDto experienceScore;    // 경력 및 경험의 구체성
        private EvalItemDto skillScore;         // 기술 / 역량 경쟁력
        private EvalItemDto growthScore;        // 성장 가능성 및 발전 잠재력
        private EvalItemDto completenessScore;  // 종합 완성도 및 논리성
    }

    /* ─────────────────────────────────────────
       항목당 점수 + 평가 텍스트
    ───────────────────────────────────────── */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvalItemDto {
        private Integer score;      // 점수 (20점 만점)
        private String evaluation;  // 평가 텍스트
    }

    /* ─────────────────────────────────────────
       예상 면접 질문
       portfolio의 QuestionDto와 동일한 구조
    ───────────────────────────────────────── */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private String q;       // 질문 내용
        private String intent;  // 질문 의도
    }
}