package com.careertalk.interview.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "interview_session_targets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ist_session_type", columnNames = {"session_id", "target_type"})
        },
        indexes = {
                @Index(name = "idx_ist_session", columnList = "session_id"),
                @Index(name = "idx_ist_target", columnList = "target_type,target_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSessionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_target_id")
    private Long sessionTargetId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    // PORTFOLIO / RESUME / ESSAY
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "analysis_id")
    private Long analysisId;

    public InterviewSessionTarget(Long sessionId, String targetType, Long targetId, Long analysisId) {
        this.sessionId = sessionId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.analysisId = analysisId;
    }

}
