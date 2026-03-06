package com.careertalk.analysis.portfolio.service;

import com.careertalk.analysis.common.service.OpenAiService;
import com.careertalk.analysis.common.entity.AnalysisEntity;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.ChartDataDto;
import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse.QuestionDto;
import com.careertalk.analysis.portfolio.entity.PortfolioEntity;
import com.careertalk.analysis.portfolio.repository.PortfolioRepository;
import com.careertalk.analysis.portfolio.util.FileParserUtil;
import com.careertalk.analysis.common.repository.AnalysisRepository;

import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.service.S3Service;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final OpenAiService openAiService;
    private final FileParserUtil fileParserUtil;

    private final S3Service s3Service;
    private final FileRepository fileRepository;

    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    @Value("classpath:prompts/portfolio-analysis-prompt.txt")
    private Resource systemPromptResource;

    @Value("${ai.model.name}")
    private String aiModelName;

    @Value("${ai.model.version}")
    private String aiModelVersion;

    @Value("${ai.prompt.version}")
    private String aiPromptVersion;

    @Transactional(noRollbackFor = RuntimeException.class)
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String jobCategory, String detailedPosition) {

        Long currentUserId = 1L;

        // 1. S3에 파일 업로드
        String s3Key = "";
        try {
            s3Key = s3Service.uploadFile(file, currentUserId);
        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }

        // 2. FileEntity DB 저장
        Long realFileId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s3Key.getBytes(StandardCharsets.UTF_8));

            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserNum(currentUserId);
            fileEntity.setFileType("PORTFOLIO");
            fileEntity.setOriginalName(file.getOriginalFilename());
            fileEntity.setMimeType(file.getContentType());
            fileEntity.setFileSize(file.getSize());
            fileEntity.setS3Bucket(s3BucketName);
            fileEntity.setS3Key(s3Key);
            fileEntity.setS3KeyHash(hash);
            fileEntity.setStatus("ACTIVE");

            FileEntity savedFile = fileRepository.save(fileEntity);
            realFileId = savedFile.getFileId();
        } catch (Exception e) {
            log.error("파일 DB 저장 실패", e);
            throw new RuntimeException("파일 정보 저장 중 오류가 발생했습니다.");
        }

        // 3. 텍스트 및 이미지 추출
        String extractedText = fileParserUtil.extractText(file);
        List<String> base64Images = fileParserUtil.extractImagesAsBase64(file);

        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userNum(currentUserId)
                .fileId(realFileId)
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

        // ⭐ 4. AI 통신 (try-catch로 감싸서 실패 처리)
        try {
            log.info("AI 분석 시작... (직무: {})", targetJobData);
            String aiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, base64Images);
            log.info("AI 분석 완료!");

            // 성공 시 SUCCESS 처리
            return processAndSaveAiResult(aiResultJson, currentUserId, portfolio.getPortfolioId(), jobCategory, "SUCCESS", null);

        } catch (Exception e) {
            log.error("AI 분석 중 에러 발생: {}", e.getMessage());

            // 실패 시 DB에 에러 메시지 저장 (status: FAIL)
            processAndSaveAiResult(null, currentUserId, portfolio.getPortfolioId(), jobCategory, "FAILED", e.getMessage());

            // 프론트엔드로 에러 던지기
            throw new RuntimeException("AI 분석 서비스에 장애가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse getAnalysisResult(Long analysisId) {
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("해당 포트폴리오의 분석 결과를 찾을 수 없습니다."));

        // 만약 분석 실패(FAIL) 건을 조회하려 한다면 프론트엔드에 빈 객체나 에러를 던져야 합니다.
        if ("FAILED".equals(analysis.getStatus())) {
            throw new RuntimeException("이 분석은 실패한 기록입니다. 에러 원인: " + analysis.getErrorMessage());
        }

        try {
            return convertToResponseDto(analysis);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 에러", e);
            throw new RuntimeException("분석 결과를 불러오는 중 오류가 발생했습니다.");
        }
    }

    private String getSystemPrompt() {
        try {
            return StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("프롬프트 파일 읽기 실패", e);
            throw new RuntimeException("서버 설정 오류로 분석을 시작할 수 없습니다.");
        }
    }


    private PortfolioAnalysisResponse processAndSaveAiResult(String aiResultJson, Long userId, Long portfolioId, String jobCategory, String status, String errorMessage) {

        // 에러 상황일 때 빈 값으로 저장
        if ("FAILED".equals(status)) {
            AnalysisEntity failedAnalysis = AnalysisEntity.builder()
                    .userNum(userId)
                    .targetType("PORTFOLIO")
                    .targetId(portfolioId)
                    .targetJob(jobCategory)
                    .status("FAILED")
                    .errorMessage(errorMessage)
                    .modelName(aiModelName)
                    .modelVersion(aiModelVersion)
                    .promptVersion(aiPromptVersion)
                    .analyzedAt(java.time.LocalDateTime.now())
                    .build();

            analysisRepository.save(failedAnalysis);
            return null;
        }

        // 성공 상황일 때 파싱해서 저장
        try {
            JsonNode rootNode = objectMapper.readTree(aiResultJson);

            int overallScore = rootNode.get("overallScore").asInt();
            String oneLineReview = rootNode.get("oneLineReview").asText();
            String summaryDetail = rootNode.get("summaryDetail").asText();
            String scoreJsonStr = rootNode.get("scoreJson").toString();
            String questionsJsonStr = rootNode.get("expectedQuestionsJson").toString();

            AnalysisEntity analysis = AnalysisEntity.builder()
                    .userNum(userId)
                    .targetType("PORTFOLIO")
                    .targetId(portfolioId)
                    .targetJob(jobCategory)
                    .overallScore(overallScore)
                    .scoreJson(scoreJsonStr)
                    .expectedQuestionsJson(questionsJsonStr)
                    .oneLineReview(oneLineReview)
                    .summaryDetail(summaryDetail)
                    .status("SUCCESS")
                    .errorMessage(null)
                    .modelName(aiModelName)
                    .modelVersion(aiModelVersion)
                    .promptVersion(aiPromptVersion)
                    .analyzedAt(java.time.LocalDateTime.now())
                    .build();

            AnalysisEntity savedAnalysis = analysisRepository.save(analysis);

            return convertToResponseDto(savedAnalysis);

        } catch (Exception e) {
            log.error("AI 응답 결과 처리 중 에러 발생", e);
            throw new RuntimeException("분석 결과를 저장하는 중 오류가 발생했습니다.");
        }
    }

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