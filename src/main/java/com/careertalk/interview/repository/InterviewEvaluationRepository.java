package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    String findPrevResultJson(@Param("userNum") Long userNum, @Param("createdAt") java.time.LocalDateTime createdAt);

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

    Optional<InterviewEvaluation> findBySessionId(Long sessionId);

    @Query("""
            SELECT
            SUM(CASE WHEN e.overallScore < :score THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.overallScore = :score THEN 1 ELSE 0 END),
            COUNT(e)
            FROM InterviewEvaluation e
            WHERE e.overallScore IS NOT NULL
            AND e.analysisStatus = 'DONE'
            AND e.sessionId <> :sessionId
            """)
    List<Object[]> getScoreStats(@Param("score") int score,
                                 @Param("sessionId") Long sessionId);

    @Query(value = """
            SELECT DATE_FORMAT(s.created_at, '%m-%d') AS label, e.overall_score AS score, s.session_id
            FROM interview_sessions s
            JOIN interview_evaluations e ON e.session_id = s.session_id
            WHERE s.user_num = :userNum
              AND s.created_at <= :currentCreatedAt
              AND e.analysis_status = 'DONE'
              AND e.overall_score IS NOT NULL
            ORDER BY s.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findScoreHistoryUntilCurrent(
            @Param("userNum") Long userNum,
            @Param("currentCreatedAt") LocalDateTime currentCreatedAt,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT e.overall_score
            FROM interview_sessions s
            JOIN interview_evaluations e ON e.session_id = s.session_id
            WHERE s.user_num = :userNum
              AND s.created_at < :currentCreatedAt
              AND e.analysis_status = 'DONE'
              AND e.overall_score IS NOT NULL
            ORDER BY s.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Integer findPreviousOverallScore(
            @Param("userNum") Long userNum,
            @Param("currentCreatedAt") LocalDateTime currentCreatedAt
    );

    @Query(value = """
            SELECT AVG(e.overall_score)
            FROM interview_evaluations e
            WHERE e.analysis_status = 'DONE'
              AND e.overall_score IS NOT NULL
            """, nativeQuery = true)
    Double findAverageOverallScore();

    @Transactional
    void deleteBySessionId(Long sessionId);
}