package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    @Query(value = """
        SELECT result_json
        FROM interview_evaluations
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    String findResultJsonBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO interview_evaluations
          (session_id, overall_score, strengths, weaknesses, next_actions, result_json, generated_at)
        VALUES
          (:sessionId, :overallScore, :strengths, :weaknesses, :nextActions, CAST(:resultJson AS JSON), NOW())
        ON DUPLICATE KEY UPDATE
          overall_score = VALUES(overall_score),
          strengths     = VALUES(strengths),
          weaknesses    = VALUES(weaknesses),
          next_actions  = VALUES(next_actions),
          result_json   = VALUES(result_json),
          generated_at  = NOW()
        """, nativeQuery = true)
    int upsert(
            @Param("sessionId") Long sessionId,
            @Param("overallScore") Integer overallScore,
            @Param("strengths") String strengths,
            @Param("weaknesses") String weaknesses,
            @Param("nextActions") String nextActions,
            @Param("resultJson") String resultJson
    );
}