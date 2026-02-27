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

// ⭐ 추가된 Import (FileEntity, Repository, S3Service 등)
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
import java.util.ArrayList;
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

    // ⭐ S3 서비스 및 File 리포지토리 의존성 주입
    private final S3Service s3Service;
    private final FileRepository fileRepository;

    @Value("${aws.s3.bucket}") // application.yml에 있는 버킷명 가져오기
    private String s3BucketName;

    @Value("classpath:prompts/portfolio-analysis-prompt.txt")
    private Resource systemPromptResource;

    @Transactional
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String jobCategory, String detailedPosition) {

        Long currentUserId = 1L;

        // ⭐ 1. S3에 파일 업로드
        String s3Key = "";
        try {
            s3Key = s3Service.uploadFile(file, currentUserId);
        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }

        // ⭐ 2. 보여주신 FileEntity 구조에 맞춰 DB에 저장 (s3KeyHash 생성 포함)
        Long realFileId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s3Key.getBytes(StandardCharsets.UTF_8));

            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserId(currentUserId);
            fileEntity.setFileType("PORTFOLIO");
            fileEntity.setOriginalName(file.getOriginalFilename());
            fileEntity.setMimeType(file.getContentType());
            fileEntity.setFileSize(file.getSize());
            fileEntity.setS3Bucket(s3BucketName);
            fileEntity.setS3Key(s3Key);
            fileEntity.setS3KeyHash(hash);
            fileEntity.setStatus("ACTIVE");

            FileEntity savedFile = fileRepository.save(fileEntity);
            realFileId = savedFile.getFileId(); // 진짜 fileId 발급 완료!
        } catch (Exception e) {
            log.error("파일 DB 저장 실패", e);
            throw new RuntimeException("파일 정보 저장 중 오류가 발생했습니다.");
        }

        // 기존 텍스트 및 이미지 추출 로직 유지
        String extractedText = fileParserUtil.extractText(file);
        List<String> base64Images = fileParserUtil.extractImagesAsBase64(file);

        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userId(currentUserId)
                .fileId(realFileId) // 발급받은 진짜 ID로 교체
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
        String aiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, base64Images);
        log.info("AI 분석 완료!");

        return processAndSaveAiResult(aiResultJson, currentUserId, portfolio.getPortfolioId(), jobCategory);
    }

    @Transactional
    public PortfolioAnalysisResponse reanalyze(Long portfolioId) {

        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("포트폴리오 정보를 찾을 수 없습니다."));

        String savedText = portfolio.getExtractedText();
        Long fileId = portfolio.getFileId(); // 저장해둔 fileId 꺼내기

        AnalysisEntity lastAnalysis = analysisRepository.findFirstByTargetIdOrderByAnalysisIdDesc(portfolioId)
                .orElseThrow(() -> new RuntimeException("이전 분석 기록을 찾을 수 없습니다."));
        String jobCategory = lastAnalysis.getTargetJob();

        // ⭐ 1. FileEntity에서 정확한 필드(getS3Key)로 경로 가져오기
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("원본 파일 정보를 찾을 수 없습니다."));
        String s3Key = fileEntity.getS3Key();

        // ⭐ 2. S3에서 파일 다운로드 및 이미지 다시 추출
        List<String> base64Images = new ArrayList<>();
        try (java.io.InputStream fileStream = s3Service.downloadFile(s3Key)) {
            base64Images = fileParserUtil.extractImagesAsBase64FromStream(fileStream, portfolio.getTitle());
            log.info("S3에서 파일을 불러와 재분석용 이미지를 추출했습니다.");
        } catch (Exception e) {
            log.error("재분석용 S3 파일 추출 실패 (텍스트로만 진행합니다)", e);
        }

        String systemPrompt = getSystemPrompt();
        String userPrompt = "지원 직무: " + jobCategory + "\n\n포트폴리오 내용:\n" + savedText;

        log.info("포트폴리오 ID: {} 재분석을 시작합니다...", portfolioId);
        String newAiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, base64Images);
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