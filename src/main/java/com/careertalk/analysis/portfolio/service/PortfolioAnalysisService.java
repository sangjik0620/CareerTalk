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
    private final ObjectMapper objectMapper; // JSON 파싱 마법사

    @Transactional
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String targetJob) {

        // 1. 파일 저장 (가정) 및 포트폴리오 엔티티 생성

        Long dummyFileId = 1L;
        Long currentUserId = 100L; // (가정) 현재 로그인한 유저 ID

        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userId(currentUserId)
                .fileId(dummyFileId)
                .title(file.getOriginalFilename())
                .status("ACTIVE")
                .build();

        // 포트폴리오 전용 레포지토리에 저장!
        portfolioRepository.save(portfolio);



        // 2. AI 분석 결과 (가짜 데이터 모킹)
        // 실제로는 여기서 AI 서버로 파일을 보내고 결과를 기다립니다.

        String mockScoreJson = "{\"직무 적합성\":90, \"문제 해결력\":95, \"성장 잠재력\":85, \"협업·소통\":80, \"프로젝트 완성도\":88}";
        String mockQuestionsJson = "[{\"q\":\"캐싱 정합성은 어떻게 해결했나요?\",\"intent\":\"기술 깊이 검증\"}, {\"q\":\"팀원과 의견 충돌 시?\",\"intent\":\"협업 능력 검증\"}]";



        // 3. 공통 분석 테이블에 결과 저장 (AnalysisEntity)

        AnalysisEntity analysis = AnalysisEntity.builder()
                .userId(currentUserId)
                .targetType("PORTFOLIO") // ⭐ 다형성: 나는 포트폴리오 분석 결과다!
                .targetId(portfolio.getPortfolioId()) // 방금 저장한 포폴 PK 연결
                .targetJob(targetJob)
                .overallScore(88)
                .scoreJson(mockScoreJson)
                .expectedQuestionsJson(mockQuestionsJson)
                .oneLineReview("실무에 바로 투입 가능한 훌륭한 포트폴리오입니다.")
                .summaryDetail("전반적인 기술 스택에 대한 이해도가 높으나, 일부 성능 최적화 경험을 더 강조하면 좋겠습니다.")
                .status("SUCCESS")
                .build();

        // 팀 공통 레포지토리에 저장
        analysisRepository.save(analysis);



        // 4. 저장된 DB 데이터를 프론트엔드용 DTO로 변환

        return convertToResponseDto(analysis);
    }

    private PortfolioAnalysisResponse convertToResponseDto(AnalysisEntity analysis) {
        try {
            // 4-1. JSON 문자열 ➡ 자바 List<QuestionDto> 로 변환
            List<QuestionDto> questionList = objectMapper.readValue(
                    analysis.getExpectedQuestionsJson(),
                    new TypeReference<List<QuestionDto>>() {}
            );

            // 4-2. JSON 문자열 ➡ Map ➡ List<ChartDataDto> 로 변환
            Map<String, Integer> scoreMap = objectMapper.readValue(
                    analysis.getScoreJson(),
                    new TypeReference<Map<String, Integer>>() {}
            );

            // Map을 돌면서 ChartDataDto로 포장
            List<ChartDataDto> chartDataList = scoreMap.entrySet().stream()
                    .map(entry -> ChartDataDto.builder()
                            .subject(entry.getKey())
                            .score(entry.getValue())
                            .fullMark(100)
                            .build())
                    .collect(Collectors.toList());

            // 4-3. 최종 포장해서 반환
            return PortfolioAnalysisResponse.builder()
                    .targetJob(analysis.getTargetJob())
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
