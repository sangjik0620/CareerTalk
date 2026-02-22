package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findBySessionIdOrderByTurnNoAsc(Long sessionId);
}