package com.careertalk.analysis.commonentity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_job", length = 100)
    private String targetJob;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "score_json", columnDefinition = "json")
    private String scoreJson;

    @Column(name = "rule_result_json", columnDefinition = "json")
    private String ruleResultJson;

    @Column(name = "one_line_review", columnDefinition = "TEXT")
    private String oneLineReview;

    @Column(name = "summary_detail", columnDefinition = "TEXT")
    private String summaryDetail;

    @Column(name = "expected_questions_json", columnDefinition = "json")
    private String expectedQuestionsJson;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AnalysisEntity(Long userId, String targetType, Long targetId, String targetJob,
                          Integer overallScore, String scoreJson, String ruleResultJson,
                          String oneLineReview, String summaryDetail, String expectedQuestionsJson,
                          String modelName, String modelVersion, String promptVersion,
                          String status, String errorMessage, LocalDateTime analyzedAt) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetJob = targetJob;
        this.overallScore = overallScore;
        this.scoreJson = scoreJson;
        this.ruleResultJson = ruleResultJson;
        this.oneLineReview = oneLineReview;
        this.summaryDetail = summaryDetail;
        this.expectedQuestionsJson = expectedQuestionsJson;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.status = status;
        this.errorMessage = errorMessage;
        this.analyzedAt = analyzedAt;
    }
}