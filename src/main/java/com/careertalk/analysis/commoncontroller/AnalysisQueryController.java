package com.careertalk.analysis.commoncontroller;

import com.careertalk.analysis.commonentity.AnalysisEntity;
import com.careertalk.analysis.commonrepository.AnalysisRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisQueryController {

    private final AnalysisRepository analysisRepository;

    @PostMapping("/batch-by-ids")
    public ResponseEntity<List<AnalysisEntity>> batchByIds(@RequestBody BatchByIdsRequest req) {
        if (req == null || req.getAnalysisIds() == null || req.getAnalysisIds().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // 1 중복 제거 + null 제거
        List<Long> ids = req.getAnalysisIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) return ResponseEntity.ok(List.of());

        // 2 조회
        List<AnalysisEntity> found = analysisRepository.findAllById(ids);

        // 3 요청 순서대로 정렬해서 반환
        Map<Long, AnalysisEntity> map = found.stream()
                .collect(Collectors.toMap(AnalysisEntity::getAnalysisId, a -> a, (a,b) -> a));

        List<AnalysisEntity> ordered = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .toList();

        return ResponseEntity.ok(ordered);
    }

    @Getter @Setter
    public static class BatchByIdsRequest {
        private List<Long> analysisIds;
    }
}