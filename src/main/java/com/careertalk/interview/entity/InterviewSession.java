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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "analysis_id")
    private Long analysisId;

    // DB에서 DEFAULT 'AI 모의면접' 줬으니 null로 insert해도 됨
    @Column(name = "title", nullable = false)
    private String title = "AI 모의면접";

    // "TEXT" or "VOICE"
    @Column(name = "mode", nullable = false)
    private String mode = "VOICE";

    // "READY" / "IN_PROGRESS" / "ENDED"
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