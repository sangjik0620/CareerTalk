package com.careertalk.analysis.coverletter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // ESSAY

    @Column(name = "target_id", nullable = false)
    private Long targetId; // essay_id

    @Column(name = "target_job", length = 100)
    private String targetJob;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Lob
    @Column(name = "score_json", columnDefinition = "json")
    private String scoreJson;

    @Lob
    @Column(name = "rule_result_json", columnDefinition = "json")
    private String ruleResultJson;

    @Lob
    @Column(name = "one_line_review", columnDefinition = "TEXT")
    private String oneLineReview;

    @Lob
    @Column(name = "summary_detail", columnDefinition = "TEXT")
    private String summaryDetail;

    @Lob
    @Column(name = "expected_questions_json", columnDefinition = "json")
    private String expectedQuestionsJson;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "status", length = 20)
    private String status; // PENDING / COMPLETED / FAILED

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}