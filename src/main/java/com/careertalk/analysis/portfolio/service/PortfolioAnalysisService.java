package com.careertalk.analysis.portfolio.service;

import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.ChartDataDto;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.QuestionDto;

import com.careertalk.analysis.portfolio.entity.PortfolioEntity;
import com.careertalk.analysis.portfolio.repository.PortfolioRepository;

import com.careertalk.analysis.commonentity.AnalysisEntity;
import com.careertalk.analysis.commonrepository.AnalysisRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {

    private final PortfolioRepository portfolioRepository;
    private final AnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    // ⭐ 파라미터가 2개(대분류, 상세)로 나뉘었습니다!
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String jobCategory, String detailedPosition) {

        // ==========================================================
        // 1. 포트폴리오 엔티티 저장
        // ==========================================================
        Long dummyFileId = 1L;
        Long currentUserId = 1L; // 실제 존재하는 유저 ID

        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userId(currentUserId)
                .fileId(dummyFileId)
                .title(file.getOriginalFilename())
                .status("ACTIVE")
                .build();

        portfolioRepository.save(portfolio);

        // ==========================================================
        // ⭐ 2. AI 분석용 텍스트 조합 (예: "IT/소프트웨어 (프론트엔드 개발자)")
        // ==========================================================
        String promptTargetJob = jobCategory;
        if (detailedPosition != null && !detailedPosition.isBlank()) {
            promptTargetJob += " (" + detailedPosition + ")";
        }

        // System.out.println("AI에게 던질 직무 프롬프트: " + promptTargetJob);
        // (가정) aiService.analyze(file, promptTargetJob);

        // ==========================================================
        // 3. AI 분석 결과 (가짜 데이터)
        // ==========================================================
        String mockScoreJson = "{\"직무 적합성\":90, \"문제 해결력\":95, \"성장 잠재력\":85, \"협업·소통\":80, \"프로젝트 완성도\":88}";
        String mockQuestionsJson = "[{\"q\":\"캐싱 정합성은 어떻게 해결했나요?\",\"intent\":\"기술 깊이 검증\"}, {\"q\":\"팀원과 의견 충돌 시?\",\"intent\":\"협업 능력 검증\"}, {\"q\":\"팀원과 의견 충돌 시?\",\"intent\":\"협업 능력 검증\"}]";

        // ==========================================================
        // ⭐ 4. 공통 분석 테이블에 결과 저장 (DB에는 깔끔하게 대분류만!)
        // ==========================================================
        AnalysisEntity analysis = AnalysisEntity.builder()
                .userId(currentUserId)
                .targetType("PORTFOLIO")
                .targetId(portfolio.getPortfolioId())
                .targetJob(jobCategory) // 👈 상세 포지션 떼고 대분류만 저장!
                .overallScore(88)
                .scoreJson(mockScoreJson)
                .expectedQuestionsJson(mockQuestionsJson)
                .oneLineReview("실무에 바로 투입 가능한 훌륭한 포트폴리오입니다.")
                .summaryDetail("전반적인 기술 스택에 대한 이해도가 높으나, 일부 성능 최적화 경험을 더 강조하면 좋겠습니다.")
                .status("SUCCESS")
                .build();

        analysisRepository.save(analysis);

        // 5. 프론트엔드용 DTO로 변환
        return convertToResponseDto(analysis);
    }

    private PortfolioAnalysisResponse convertToResponseDto(AnalysisEntity analysis) {
        try {
            List<QuestionDto> questionList = objectMapper.readValue(
                    analysis.getExpectedQuestionsJson(),
                    new TypeReference<List<QuestionDto>>() {}
            );

            Map<String, Integer> scoreMap = objectMapper.readValue(
                    analysis.getScoreJson(),
                    new TypeReference<Map<String, Integer>>() {}
            );

            List<ChartDataDto> chartDataList = scoreMap.entrySet().stream()
                    .map(entry -> ChartDataDto.builder()
                            .subject(entry.getKey())
                            .score(entry.getValue())
                            .fullMark(100)
                            .build())
                    .collect(Collectors.toList());

            return PortfolioAnalysisResponse.builder()
                    .targetJob(analysis.getTargetJob())
                    .overallScore(analysis.getOverallScore())
                    .oneLineReview(analysis.getOneLineReview())
                    .summaryDetail(analysis.getSummaryDetail())
                    .chartData(chartDataList)
                    .questions(questionList)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 에러 발생", e);
            throw new RuntimeException("분석 결과를 처리하는 중 오류가 발생했습니다.");
        }
    }
}
