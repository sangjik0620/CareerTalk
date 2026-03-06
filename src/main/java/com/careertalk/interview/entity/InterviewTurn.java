package com.careertalk.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "interview_turns",
        uniqueConstraints = @UniqueConstraint(name = "uk_turns_session_turnno", columnNames = {"session_id", "turn_no"}))
public class InterviewTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turn_id")
    private Long turnId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "turn_no", nullable = false)
    private Integer turnNo;

    @Lob
    @Column(name = "ai_question", nullable = false)
    private String aiQuestion;

    @Lob
    @Column(name = "user_answer_text")
    private String userAnswerText;

    @Column(name = "answer_audio_file_id")
    private Long answerAudioFileId;

    @Lob
    @Column(name = "stt_text")
    private String sttText;

    // ✅ STT 상태/재시도/에러
    @Enumerated(EnumType.STRING)
    @Column(name = "stt_status", nullable = false, length = 20)
    private SttStatus sttStatus = SttStatus.PENDING;

    @Column(name = "stt_attempt_count", nullable = false)
    private Integer sttAttemptCount = 0;

    @Lob
    @Column(name = "stt_error_message")
    private String sttErrorMessage;

    @Column(name = "stt_started_at")
    private LocalDateTime sttStartedAt;

    @Column(name = "stt_completed_at")
    private LocalDateTime sttCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    @Column(name = "answer_audio_duration_sec")
    private Integer answerAudioDurationSec;

    @Column(name = "feedback_json", columnDefinition = "JSON")
    private String feedbackJson;

    @Column(name="audio_metrics_json", columnDefinition="JSON")
    private String audioMetricsJson;

    @Column(name = "python_metrics_json", columnDefinition = "json") // TEXT면 columnDefinition 지워도 됨
    private String pythonMetricsJson;

    @Column(name = "audio_scores_json", columnDefinition = "json")
    private String audioScoresJson;

    // ===== Turn Analysis 상태/재시도/에러 (FastAPI metrics) =====
    @Enumerated(EnumType.STRING)
    @Column(name = "turn_analysis_status", nullable = false, length = 20)
    private TurnAnalysisStatus turnAnalysisStatus = TurnAnalysisStatus.PENDING;

    @Column(name = "turn_analysis_attempt_count", nullable = false)
    private Integer turnAnalysisAttemptCount = 0;

    @Lob
    @Column(name = "turn_analysis_error_message")
    private String turnAnalysisErrorMessage;

    @Column(name = "turn_analysis_started_at")
    private LocalDateTime turnAnalysisStartedAt;

    @Column(name = "turn_analysis_completed_at")
    private LocalDateTime turnAnalysisCompletedAt;


    // ===== Turn Score 상태/재시도/에러 (Java scoring) =====
    @Enumerated(EnumType.STRING)
    @Column(name = "turn_score_status", nullable = false, length = 20)
    private TurnScoreStatus turnScoreStatus = TurnScoreStatus.PENDING;

    @Column(name = "turn_score_attempt_count", nullable = false)
    private Integer turnScoreAttemptCount = 0;

    @Lob
    @Column(name = "turn_score_error_message")
    private String turnScoreErrorMessage;

    @Column(name = "turn_score_started_at")
    private LocalDateTime turnScoreStartedAt;

    @Column(name = "turn_score_completed_at")
    private LocalDateTime turnScoreCompletedAt;
}