package com.careertalk.interview.controller;

import com.careertalk.interview.repository.InterviewSessionTargetRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interview/sessions")
@RequiredArgsConstructor
public class InterviewSessionTargetController {

    private final InterviewSessionTargetRepository repo;

    @GetMapping("/{sessionId}/targets")
    public ResponseEntity<List<SessionTargetDto>> getTargets(@PathVariable Long sessionId) {

        var list = repo.findBySessionId(sessionId).stream()
                .map(t -> new SessionTargetDto(
                        t.getTargetType(),
                        t.getTargetId(),
                        t.getAnalysisId()
                ))
                .toList();

        return ResponseEntity.ok(list);
    }

    @Getter
    @AllArgsConstructor
    public static class SessionTargetDto {
        private String targetType;
        private Long targetId;
        private Long analysisId;
    }
}