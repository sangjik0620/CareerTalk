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
    // 상태 조회
    @Query(value = """
    SELECT analysis_status
    FROM interview_evaluations
    WHERE session_id = :sessionId
    """, nativeQuery = true)
    String findAnalysisStatusBySessionId(@Param("sessionId") Long sessionId);

    // 상태 업데이트
    @Modifying
    @Transactional
    @Query(value = """
    UPDATE interview_evaluations
    SET analysis_status = :status,
        analysis_started_at = CASE WHEN :status = 'PROCESSING' THEN NOW() ELSE analysis_started_at END,
        analysis_completed_at = CASE WHEN :status IN ('DONE','FAILED') THEN NOW() ELSE analysis_completed_at END,
        analysis_error_message = :errorMessage
    WHERE session_id = :sessionId
    """, nativeQuery = true)
    int updateAnalysisStatus(
            @Param("sessionId") Long sessionId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    // 직전(이전) 점수: 같은 userId의 현재 session created_at 이전 중 최신 1개
    @Query(value = """
    SELECT ie.overall_score
    FROM interview_sessions s
    JOIN interview_evaluations ie ON ie.session_id = s.session_id
    WHERE s.user_num = :userNum
      AND ie.analysis_status = 'DONE'
      AND ie.overall_score IS NOT NULL
      AND s.created_at < :createdAt
    ORDER BY s.created_at DESC
    LIMIT 1
    """, nativeQuery = true)
    Integer findPrevOverallScore(
            @Param("userId") Long userId,
            @Param("createdAt") java.time.LocalDateTime createdAt
    );

    // 전체 평균 점수: DONE 평가 평균 (현재 세션은 제외)
    @Query(value = """
    SELECT AVG(overall_score)
    FROM interview_evaluations
    WHERE analysis_status = 'DONE'
      AND overall_score IS NOT NULL
      AND session_id <> :sessionId
    """, nativeQuery = true)
    Double findGlobalAverageScoreExcludingSession(@Param("sessionId") Long sessionId);

    @Query(value = """
    SELECT
      CASE
        WHEN COUNT(*) = 0 THEN NULL
        ELSE ROUND(
          (SUM(CASE WHEN overall_score < :overallScore THEN 1 ELSE 0 END) * 100.0) / COUNT(*)
        )
      END AS percentile_rank
    FROM interview_evaluations
    WHERE analysis_status = 'DONE'
      AND overall_score IS NOT NULL
      AND overall_score > 0
      AND session_id <> :sessionId
    """, nativeQuery = true)
    Double findPercentileRankExcludingSession(
            @Param("sessionId") Long sessionId,
            @Param("overallScore") Integer overallScore
    );
    @Query(value = """
    SELECT DATE_FORMAT(s.created_at, '%Y-%m') AS ym, e.overall_score AS score
    FROM interview_sessions s
    JOIN interview_evaluations e ON e.session_id = s.session_id
    WHERE s.user_num = :userNum
      AND e.analysis_status = 'DONE'
      AND e.overall_score IS NOT NULL
    ORDER BY s.created_at DESC
    LIMIT :limit
    """, nativeQuery = true)
    java.util.List<Object[]> findRecentScoreHistory(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(value = """
    SELECT e.result_json
    FROM interview_sessions s
    JOIN interview_evaluations e ON e.session_id = s.session_id
    WHERE s.user_num = :userNum
      AND s.created_at < :createdAt
      AND e.analysis_status = 'DONE'
      AND e.result_json IS NOT NULL
    ORDER BY s.created_at DESC
    LIMIT 1
    """, nativeQuery = true)
    String findPrevResultJson(@Param("userId") Long userId, @Param("createdAt") java.time.LocalDateTime createdAt);

    @Query(value = """
    SELECT AVG(CAST(JSON_UNQUOTE(JSON_EXTRACT(result_json, :jsonPath)) AS DECIMAL(10,2)))
    FROM interview_evaluations
    WHERE analysis_status = 'DONE'
      AND result_json IS NOT NULL
      AND session_id <> :sessionId
    """, nativeQuery = true)
    Double avgFromResultJson(@Param("sessionId") Long sessionId, @Param("jsonPath") String jsonPath);

    @Modifying
    @Transactional
    @Query(value = """
UPDATE interview_evaluations
SET analysis_status = 'PROCESSING'
WHERE session_id = :sessionId
AND (analysis_status IS NULL OR analysis_status IN ('PENDING','FAILED'))
""", nativeQuery = true)
    int markProcessingIfPossible(@Param("sessionId") Long sessionId);



}