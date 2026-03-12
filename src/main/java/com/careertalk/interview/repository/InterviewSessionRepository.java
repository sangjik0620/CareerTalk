package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findAllByUserNumOrderByCreatedAtDesc(Long userNum);

    @Transactional
    void deleteBySessionId(Long sessionId);
}
