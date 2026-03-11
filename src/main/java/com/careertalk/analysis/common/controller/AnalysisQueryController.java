package com.careertalk.analysis.common.controller;

import com.careertalk.analysis.common.entity.AnalysisEntity;
import com.careertalk.analysis.common.repository.AnalysisRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.service.MemberService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisQueryController {

    private final AnalysisRepository analysisRepository;
    private final JwtUtil jwtUtil;
    private final MemberService memberService;

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

    // 마이페이지 분석 탭 데이터 조회 API
    @GetMapping("/my")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getMyAnalyses(
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 1. 토큰 유효성 검사
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        try {
            // 2. 토큰에서 loginId 추출
            String jwtToken = token.substring(7);
            String loginId = jwtUtil.getLoginId(jwtToken);

            // 3. Member 조회 후 PK(userNum) 추출
            Member member = memberService.findByLoginId(loginId);
            if (member == null) {
                return ResponseEntity.status(404).build();
            }
            Long userNum = member.getUserNum();

            // 4. DB에서 내 분석 기록 전체 조회 (최신순)
            List<AnalysisEntity> myAnalyses = analysisRepository.findAllByUserNumOrderByAnalyzedAtDesc(userNum);

            // 5. 프론트엔드가 원하는 형태로 데이터 가공 및 분류
            List<Map<String, Object>> resumeList = new ArrayList<>();
            List<Map<String, Object>> coverLetterList = new ArrayList<>();
            List<Map<String, Object>> portfolioList = new ArrayList<>();

            for (AnalysisEntity entity : myAnalyses) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", entity.getAnalysisId());

                String displayDate = (entity.getAnalyzedAt() != null)
                        ? entity.getAnalyzedAt().toString().substring(0, 10)
                        : entity.getCreatedAt().toString().substring(0, 10);
                dto.put("date", displayDate);

                // 1. 점수 (overall_score 컬럼 사용)
                Integer score = entity.getOverallScore();
                dto.put("score", score != null ? score : 0); // 점수가 비어있으면 0점 처리

                // 2. 제목 (target_job 컬럼을 활용해서 제목 만들기)
                String job = entity.getTargetJob() != null ? entity.getTargetJob() : "미지정 직무";

                if ("RESUME".equalsIgnoreCase(entity.getTargetType())) {
                    dto.put("title", job + " 이력서 분석 리포트");
                } else if ("ESSAY".equalsIgnoreCase(entity.getTargetType())) {
                    dto.put("title", job + " 자기소개서 분석 리포트");
                } else if ("PORTFOLIO".equalsIgnoreCase(entity.getTargetType())) {
                    dto.put("title", job + " 포트폴리오 분석 리포트");
                }

                // 타겟 타입별로 리스트에 담기
                if ("RESUME".equalsIgnoreCase(entity.getTargetType())) {
                    resumeList.add(dto);
                } else if ("ESSAY".equalsIgnoreCase(entity.getTargetType())) {
                    coverLetterList.add(dto);
                } else if ("PORTFOLIO".equalsIgnoreCase(entity.getTargetType())) {
                    portfolioList.add(dto);
                }
            }

            // 6. 프론트엔드와 약속한 JSON 구조로 응답
            Map<String, List<Map<String, Object>>> response = new HashMap<>();
            response.put("resume", resumeList);
            response.put("coverLetter", coverLetterList);
            response.put("portfolio", portfolioList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(401).build(); // 토큰 만료 등 에러 시 401 반환
        }
    }
}