package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewRecording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRecordingRepository extends JpaRepository<InterviewRecording, Long> {

    // ❌ findBySessionIdOrderByQuestionIndexAsc(Long sessionId)

    // ✅ session.sessionId 로 타고 들어가야 함
    List<InterviewRecording> findBySession_SessionIdOrderByQuestionIndexAsc(Long sessionId);
}