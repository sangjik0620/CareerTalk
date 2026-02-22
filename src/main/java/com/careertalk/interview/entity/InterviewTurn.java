package com.careertalk.interview.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "feedback_json", columnDefinition = "json")
    private String feedbackJson;
}