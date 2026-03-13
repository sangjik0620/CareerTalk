package com.careertalk.interview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "title", nullable = false)
    private String title = "AI 모의면접";

    @Column(name = "job_category")
    private String jobCategory;

    @Column(name = "mode", nullable = false)
    private String mode = "VOICE";

    @Column(name = "status", nullable = false)
    private String status = "READY";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}