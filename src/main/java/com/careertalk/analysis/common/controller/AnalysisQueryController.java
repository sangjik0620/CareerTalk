package com.careertalk.analysis.common.controller;

import com.careertalk.analysis.common.dto.AnalysisHistoryResponse;
import com.careertalk.analysis.common.entity.AnalysisEntity;
import com.careertalk.analysis.common.repository.AnalysisRepository;
import com.careertalk.analysis.coverletter.entity.CIEssay;
import com.careertalk.analysis.coverletter.repository.CIEssayRepository;
import com.careertalk.analysis.portfolio.entity.PortfolioEntity;
import com.careertalk.analysis.portfolio.repository.PortfolioRepository;
import com.careertalk.analysis.resume.entity.ResumeEntity;
import com.careertalk.analysis.resume.repository.ResumeRepository;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.repository.MemberRepository;
import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.service.MemberService;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisQueryController {

    private final AnalysisRepository analysisRepository;
    private final JwtUtil jwtUtil;
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final ResumeRepository resumeRepository;
    private final PortfolioRepository portfolioRepository;
    private final CIEssayRepository ciEssayRepository;
    private final FileRepository fileRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/batch-by-ids")
    public ResponseEntity<List<AnalysisEntity>> batchByIds(@RequestBody BatchByIdsRequest req) {
        if (req == null || req.getAnalysisIds() == null || req.getAnalysisIds().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> ids = req.getAnalysisIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<AnalysisEntity> found = analysisRepository.findAllById(ids);

        Map<Long, AnalysisEntity> map = found.stream()
                .collect(Collectors.toMap(AnalysisEntity::getAnalysisId, a -> a, (a, b) -> a));

        List<AnalysisEntity> ordered = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .toList();

        return ResponseEntity.ok(ordered);
    }

    @Getter
    @Setter
    public static class BatchByIdsRequest {
        private List<Long> analysisIds;
    }

    @GetMapping("/my")
    public ResponseEntity<AnalysisHistoryResponse> getMyAnalyses(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userNum = extractUserNum(authHeader);

        List<AnalysisEntity> list = analysisRepository.findByUserNumOrderByCreatedAtDesc(userNum);

        List<AnalysisHistoryResponse.Item> resume = new ArrayList<>();
        List<AnalysisHistoryResponse.Item> coverLetter = new ArrayList<>();
        List<AnalysisHistoryResponse.Item> portfolio = new ArrayList<>();

        for (AnalysisEntity a : list) {
            String type = a.getTargetType();
            AnalysisHistoryResponse.Item item = null;

            if ("RESUME".equalsIgnoreCase(type)) {
                item = resumeRepository.findById(a.getTargetId())
                        .map(r -> toResumeItem(a, r))
                        .orElse(null);

                if (item != null) {
                    resume.add(item);
                }

            } else if ("ESSAY".equalsIgnoreCase(type)) {
                item = ciEssayRepository.findById(a.getTargetId())
                        .map(e -> toEssayItem(a, e))
                        .orElse(null);

                if (item != null) {
                    coverLetter.add(item);
                }

            } else if ("PORTFOLIO".equalsIgnoreCase(type)) {
                item = portfolioRepository.findById(a.getTargetId())
                        .map(p -> toPortfolioItem(a, p))
                        .orElse(null);

                if (item != null) {
                    portfolio.add(item);
                }
            }
        }

        return ResponseEntity.ok(
                AnalysisHistoryResponse.builder()
                        .resume(resume)
                        .coverLetter(coverLetter)
                        .portfolio(portfolio)
                        .build()
        );
    }

    private Long extractUserNum(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("유효하지 않은 토큰입니다.");
        }

        String loginId = jwtUtil.getLoginId(token);

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        return member.getUserNum();
    }

    private AnalysisHistoryResponse.Item toResumeItem(AnalysisEntity a, ResumeEntity r) {
        String fileName = fileRepository.findById(r.getFileId())
                .map(FileEntity::getOriginalName)
                .orElse("");

        return AnalysisHistoryResponse.Item.builder()
                .id(a.getAnalysisId())
                .targetId(r.getResumeId())
                .analysisId(a.getAnalysisId())
                .title(r.getResumeTitle())
                .fileName(fileName)
                .analyzedAt(formatDate(a.getAnalyzedAt(), a.getCreatedAt()))
                .score(a.getOverallScore() == null ? 0 : a.getOverallScore())
                .keywords(extractKeywords(a.getTargetJob(), a.getSummaryDetail()))
                .expectedQuestions(extractExpectedQuestions(a.getExpectedQuestionsJson()))
                .build();
    }

    private AnalysisHistoryResponse.Item toEssayItem(AnalysisEntity a, CIEssay e) {
        String fileName = e.getFileId() == null
                ? ""
                : fileRepository.findById(e.getFileId())
                .map(FileEntity::getOriginalName)
                .orElse("");

        return AnalysisHistoryResponse.Item.builder()
                .id(a.getAnalysisId())
                .targetId(e.getEssayId())
                .analysisId(a.getAnalysisId())
                .title(e.getTitle() == null || e.getTitle().isBlank() ? "자기소개서" : e.getTitle())
                .fileName(fileName)
                .analyzedAt(formatDate(a.getAnalyzedAt(), a.getCreatedAt()))
                .score(a.getOverallScore() == null ? 0 : a.getOverallScore())
                .keywords(extractKeywords(a.getTargetJob(), a.getSummaryDetail()))
                .expectedQuestions(extractExpectedQuestions(a.getExpectedQuestionsJson()))
                .build();
    }

    private AnalysisHistoryResponse.Item toPortfolioItem(AnalysisEntity a, PortfolioEntity p) {
        String fileName = fileRepository.findById(p.getFileId())
                .map(FileEntity::getOriginalName)
                .orElse("");

        return AnalysisHistoryResponse.Item.builder()
                .id(a.getAnalysisId())
                .targetId(p.getPortfolioId())
                .analysisId(a.getAnalysisId())
                .title(p.getTitle())
                .fileName(fileName)
                .analyzedAt(formatDate(a.getAnalyzedAt(), a.getCreatedAt()))
                .score(a.getOverallScore() == null ? 0 : a.getOverallScore())
                .keywords(extractKeywords(a.getTargetJob(), a.getSummaryDetail()))
                .expectedQuestions(extractExpectedQuestions(a.getExpectedQuestionsJson()))
                .build();
    }

    private String formatDate(java.time.LocalDateTime analyzedAt, java.time.LocalDateTime createdAt) {
        java.time.LocalDateTime base = analyzedAt != null ? analyzedAt : createdAt;
        return base == null ? "" : base.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private List<String> extractKeywords(String targetJob, String summaryDetail) {
        List<String> result = new ArrayList<>();

        if (targetJob != null && !targetJob.isBlank()) {
            result.add(targetJob);
        }

        if (summaryDetail != null && !summaryDetail.isBlank()) {
            String[] tokens = summaryDetail
                    .replaceAll("[^가-힣a-zA-Z0-9 ]", " ")
                    .split("\\s+");

            for (String t : tokens) {
                if (t.length() >= 2 && result.size() < 3 && !result.contains(t)) {
                    result.add(t);
                }
            }
        }

        if (result.isEmpty()) {
            result.add("분석 완료");
        }

        return result.stream().limit(3).collect(Collectors.toList());
    }

    private List<String> extractExpectedQuestions(String expectedQuestionsJson) {
        if (expectedQuestionsJson == null || expectedQuestionsJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(expectedQuestionsJson);
            List<String> result = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode node : root) {
                    String q = node.path("q").asText(null);
                    if (q != null && !q.isBlank()) {
                        result.add(q.trim());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}