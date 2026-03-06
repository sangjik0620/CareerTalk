package com.careertalk.interview.repository;

import com.careertalk.interview.entity.InterviewSessionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionTargetRepository extends JpaRepository<InterviewSessionTarget, Long> {

    List<InterviewSessionTarget> findBySessionId(Long sessionId);

    Optional<InterviewSessionTarget> findBySessionIdAndTargetType(Long sessionId, String targetType);
}