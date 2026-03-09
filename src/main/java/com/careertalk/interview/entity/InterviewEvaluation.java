package com.careertalk.interview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "interview_evaluations",
        uniqueConstraints = @UniqueConstraint(name = "uq_interview_evaluations_session", columnNames = "session_id")
)
public class InterviewEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eval_id")
    private Long evalId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "TEXT")
    private String weaknesses;

    @Column(name = "next_actions", columnDefinition = "TEXT")
    private String nextActions;

    @Column(name = "result_json", columnDefinition = "JSON")
    private String resultJson;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Column(name = "analysis_started_at")
    private LocalDateTime analysisStartedAt;

    @Column(name = "analysis_completed_at")
    private LocalDateTime analysisCompletedAt;

    @Lob
    @Column(name = "analysis_error_message")
    private String analysisErrorMessage;

}


