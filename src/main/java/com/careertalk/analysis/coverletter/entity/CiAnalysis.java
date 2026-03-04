package com.careertalk.analysis.coverletter.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ci_analysis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    private Long userId;

    private String targetType; // TEXT, FILE

    private Long targetId;

    private String targetJob;

    private Integer overallScore;

    @Column(columnDefinition = "json")
    private String scoreJson;

    @Column(columnDefinition = "json")
    private String expectedQuestionsJson;

    private String modelName;
    private String modelVersion;
    private String promptVersion;

    private String status; // PROCESSING, SUCCESS, FAILED

    @Column(columnDefinition = "text")
    private String errorMessage;

    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}