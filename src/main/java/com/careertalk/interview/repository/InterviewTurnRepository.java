package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findBySessionIdOrderByTurnNoAsc(Long sessionId);

    @Query("""
            SELECT t.answerAudioFileId
            FROM InterviewTurn t
            WHERE t.sessionId = :sessionId
              AND t.answerAudioFileId IS NOT NULL
            """)
    List<Long> findAudioFileIdsBySessionId(@Param("sessionId") Long sessionId);

    @Transactional
    void deleteBySessionId(Long sessionId);
}