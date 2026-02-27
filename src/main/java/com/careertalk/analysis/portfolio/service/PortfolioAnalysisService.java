package com.careertalk.analysis.portfolio.service;

import com.careertalk.analysis.commonservice.OpenAiService;
import com.careertalk.analysis.commonentity.AnalysisEntity;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.ChartDataDto;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.QuestionDto;
import com.careertalk.analysis.portfolio.entity.PortfolioEntity;
import com.careertalk.analysis.portfolio.repository.PortfolioRepository;
import com.careertalk.analysis.portfolio.util.FileParserUtil;
import com.careertalk.analysis.commonrepository.AnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    // 조립할 부품들 주입
    private final OpenAiService openAiService;
    private final FileParserUtil fileParserUtil;

    // 프롬프트 파일 불러오기
    @Value("classpath:prompts/portfolio-analysis-prompt.txt")
    private Resource systemPromptResource;


    // 1. 최초 분석 기능

    @Transactional
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String jobCategory, String detailedPosition) {

        String extractedText = fileParserUtil.extractText(file);
        log.info("파일에서 추출된 텍스트 길이: {}자", extractedText.length());

        // ⭐ 1. 썸네일 이미지 리스트 추출
        List<String> base64Images = fileParserUtil.extractImagesAsBase64(file);

        Long currentUserId = 1L;
        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userId(currentUserId)
                .fileId(1L)
                .title(file.getOriginalFilename())
                .extractedText(extractedText)
                .status("ACTIVE")
                .build();
        portfolioRepository.save(portfolio);

        String systemPrompt = getSystemPrompt();
        String targetJobData = (detailedPosition != null && !detailedPosition.isBlank())
                ? jobCategory + " (" + detailedPosition + ")"
                : jobCategory;
        String userPrompt = "지원 직무: " + targetJobData + "\n\n포트폴리오 내용:\n" + extractedText;

        log.info("AI 분석 시작... (직무: {})", targetJobData);
        // ⭐ 2. AI 호출 시 이미지 리스트 함께 전달
        String aiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, base64Images);
        log.info("AI 분석 완료!");

        return processAndSaveAiResult(aiResultJson, currentUserId, portfolio.getPortfolioId(), jobCategory);
    }


    // 2. 재분석 기능

    @Transactional
    public PortfolioAnalysisResponse reanalyze(Long portfolioId) {

        // 1. DB에서 기존 포트폴리오 찾기 (텍스트가 저장되어 있음!)
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("포트폴리오 정보를 찾을 수 없습니다."));

        String savedText = portfolio.getExtractedText();
        if (savedText == null || savedText.isBlank()) {
            throw new RuntimeException("저장된 포트폴리오 텍스트가 없습니다. 원본 파일을 다시 업로드해주세요.");
        }

        // 2. 직무 정보(jobCategory)를 가져오기 위해 가장 최근 분석 기록 찾기

        AnalysisEntity lastAnalysis = analysisRepository.findFirstByTargetIdOrderByAnalysisIdDesc(portfolioId)
                .orElseThrow(() -> new RuntimeException("이전 분석 기록을 찾을 수 없습니다."));

        String jobCategory = lastAnalysis.getTargetJob();

        // 3. 프롬프트 조립
        String systemPrompt = getSystemPrompt();
        String userPrompt = "지원 직무: " + jobCategory + "\n\n포트폴리오 내용:\n" + savedText;

        log.info("포트폴리오 ID: {} 재분석을 시작합니다... (파일 추출 생략)", portfolioId);
        // ⭐ 3. 재분석 시에는 원본 파일이 없으므로 이미지에 null 전달 (텍스트만으로 재분석)
        String newAiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, null);
        log.info("AI 재분석 완료!");

        return processAndSaveAiResult(newAiResultJson, portfolio.getUserId(), portfolioId, jobCategory);
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse getAnalysisResult(Long analysisId) {

        // 1. DB에서 해당 포트폴리오의 가장 최근 분석 기록을 찾습니다.
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("해당 포트폴리오의 분석 결과를 찾을 수 없습니다."));

        // 2. 찾아낸 엔티티를 프론트엔드가 좋아하는 DTO 형태로 변환해서 돌려줍니다!
        try {
            return convertToResponseDto(analysis);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 에러", e);
            throw new RuntimeException("분석 결과를 불러오는 중 오류가 발생했습니다.");
        }
    }


    // 프롬프트 텍스트 파일 읽어오는 공통 메서드
    private String getSystemPrompt() {
        try {
            return StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("프롬프트 파일 읽기 실패", e);
            throw new RuntimeException("서버 설정 오류로 분석을 시작할 수 없습니다.");
        }
    }

    // AI 응답 JSON을 파싱해서 DB에 넣고 DTO로 변환하는 공통 메서드
    private PortfolioAnalysisResponse processAndSaveAiResult(String aiResultJson, Long userId, Long portfolioId, String jobCategory) {
        try {
            JsonNode rootNode = objectMapper.readTree(aiResultJson);

            int overallScore = rootNode.get("overallScore").asInt();
            String oneLineReview = rootNode.get("oneLineReview").asText();
            String summaryDetail = rootNode.get("summaryDetail").asText();
            String scoreJsonStr = rootNode.get("scoreJson").toString();
            String questionsJsonStr = rootNode.get("expectedQuestionsJson").toString();

            AnalysisEntity analysis = AnalysisEntity.builder()
                    .userId(userId)
                    .targetType("PORTFOLIO")
                    .targetId(portfolioId)
                    .targetJob(jobCategory)
                    .overallScore(overallScore)
                    .scoreJson(scoreJsonStr)
                    .expectedQuestionsJson(questionsJsonStr)
                    .oneLineReview(oneLineReview)
                    .summaryDetail(summaryDetail)
                    .status("SUCCESS")
                    .build();

            AnalysisEntity savedAnalysis = analysisRepository.save(analysis);

            return convertToResponseDto(savedAnalysis);

        } catch (Exception e) {
            log.error("AI 응답 결과 처리 중 에러 발생", e);
            throw new RuntimeException("분석 결과를 저장하는 중 오류가 발생했습니다.");
        }
    }

    // 엔티티를 DTO로 변환
    private PortfolioAnalysisResponse convertToResponseDto(AnalysisEntity analysis) throws JsonProcessingException {
        List<QuestionDto> questionList = objectMapper.readValue(
                analysis.getExpectedQuestionsJson(), new TypeReference<>() {}
        );
        Map<String, Integer> scoreMap = objectMapper.readValue(
                analysis.getScoreJson(), new TypeReference<>() {}
        );

        List<ChartDataDto> chartDataList = scoreMap.entrySet().stream()
                .map(entry -> ChartDataDto.builder()
                        .subject(entry.getKey())
                        .score(entry.getValue())
                        .fullMark(100)
                        .build())
                .collect(Collectors.toList());

        return PortfolioAnalysisResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .portfolioId(analysis.getTargetId())
                .targetJob(analysis.getTargetJob())
                .overallScore(analysis.getOverallScore())
                .oneLineReview(analysis.getOneLineReview())
                .summaryDetail(analysis.getSummaryDetail())
                .chartData(chartDataList)
                .questions(questionList)
                .build();
    }
}