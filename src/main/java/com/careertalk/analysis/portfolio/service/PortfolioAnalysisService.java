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

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.service.S3Service;

// 이용권 관련 임포트
import com.careertalk.payment.entity.UserUsageQuota;
import com.careertalk.payment.repository.UserUsageQuotaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    private final MemberRepository memberRepository;
    private final UserUsageQuotaRepository userUsageQuotaRepository;

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
    public PortfolioAnalysisResponse analyzeAndSave(MultipartFile file, String jobCategory, String detailedPosition, String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("해당 아이디의 회원을 찾을 수 없습니다: " + loginId));
        Long userNum = member.getUserNum();
        String nickname = member.getNickname();

        //  분석 시작 전 이용권 잔여 횟수 검증
        UserUsageQuota quota = userUsageQuotaRepository.findByUserNum(userNum)
                .orElseThrow(() -> new RuntimeException("이용권 정보를 찾을 수 없습니다."));

        int totalAnalysisRemaining = quota.getFreeAnalysisRemaining() + quota.getPaidAnalysisRemaining();
        if (totalAnalysisRemaining <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INSUFFICIENT_QUOTA");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new RuntimeException("현재는 PDF 형식의 포트폴리오만 분석이 가능합니다.");
        }

        String s3Key;
        try {
            s3Key = s3Service.uploadFile(file, userNum);
        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }

        Long realFileId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s3Key.getBytes(StandardCharsets.UTF_8));
            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserNum(userNum);
            fileEntity.setFileType("PORTFOLIO");
            fileEntity.setOriginalName(originalFilename);
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

        String extractedText = fileParserUtil.extractText(file);
        PortfolioEntity portfolio = PortfolioEntity.builder()
                .userNum(userNum)
                .fileId(realFileId)
                .title(originalFilename)
                .extractedText(extractedText)
                .status("ACTIVE")
                .build();
        portfolioRepository.save(portfolio);

        List<String> base64Images = fileParserUtil.extractImagesAsBase64(file);
        String systemPrompt = getSystemPrompt();
        String targetJobData = (detailedPosition != null && !detailedPosition.isBlank())
                ? jobCategory + " (" + detailedPosition + ")"
                : jobCategory;
        String userPrompt = "지원 직무: " + targetJobData + "\n\n포트폴리오 내용:\n" + extractedText;

        try {
            String aiResultJson = openAiService.getAiResponse(systemPrompt, userPrompt, base64Images);
            PortfolioAnalysisResponse response = processAndSaveAiResult(aiResultJson, userNum, nickname, portfolio.getPortfolioId(), jobCategory, "SUCCESS", null);

            // 분석 성공 시 이용권 1회 차감
            if (quota.getFreeAnalysisRemaining() > 0) {
                quota.setFreeAnalysisRemaining(quota.getFreeAnalysisRemaining() - 1);
            } else {
                quota.setPaidAnalysisRemaining(quota.getPaidAnalysisRemaining() - 1);
            }

            return response;
        } catch (Exception e) {
            log.error("AI 분석 중 에러 발생: {}", e.getMessage());
            processAndSaveAiResult(null, userNum, nickname, portfolio.getPortfolioId(), jobCategory, "FAILED", e.getMessage());
            throw new RuntimeException("AI 분석 서비스 장애가 발생했습니다.");
        }
    }

    private PortfolioAnalysisResponse processAndSaveAiResult(String aiResultJson, Long userId, String nickname, Long portfolioId, String jobCategory, String status, String errorMessage) {
        if ("FAILED".equals(status)) {
            AnalysisEntity failedAnalysis = AnalysisEntity.builder()
                    .userNum(userId).targetType("PORTFOLIO").targetId(portfolioId).targetJob(jobCategory)
                    .status("FAILED").errorMessage(errorMessage).analyzedAt(java.time.LocalDateTime.now()).build();
            analysisRepository.save(failedAnalysis);
            return null;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(aiResultJson);
            AnalysisEntity analysis = AnalysisEntity.builder()
                    .userNum(userId).targetType("PORTFOLIO").targetId(portfolioId).targetJob(jobCategory)
                    .overallScore(rootNode.get("overallScore").asInt())
                    .scoreJson(rootNode.get("scoreJson").toString())
                    .expectedQuestionsJson(rootNode.get("expectedQuestionsJson").toString())
                    .oneLineReview(rootNode.get("oneLineReview").asText())
                    .summaryDetail(rootNode.get("summaryDetail").asText())
                    .status("SUCCESS").modelName(aiModelName).modelVersion(aiModelVersion).promptVersion(aiPromptVersion)
                    .analyzedAt(java.time.LocalDateTime.now()).build();

            AnalysisEntity savedAnalysis = analysisRepository.save(analysis);
            return convertToResponseDto(savedAnalysis, nickname);
        } catch (Exception e) {
            throw new RuntimeException("분석 결과 처리 중 오류 발생");
        }
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse getAnalysisResult(Long analysisId, Long userNum) {
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("결과를 찾을 수 없습니다."));

        if (!analysis.getUserNum().equals(userNum)) {
            throw new RuntimeException("해당 결과에 대한 접근 권한이 없습니다.");
        }

        Member member = (Member) memberRepository.findByUserNum(userNum)
                .orElseThrow(() -> new RuntimeException("유저 정보를 찾을 수 없습니다."));

        try {
            return convertToResponseDto(analysis, member.getNickname());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("데이터 변환 오류");
        }
    }

    private String getSystemPrompt() {
        try {
            return StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("프롬프트 파일을 읽을 수 없습니다.");
        }
    }

    private PortfolioAnalysisResponse convertToResponseDto(AnalysisEntity analysis, String nickname) throws JsonProcessingException {

        // 예상 질문 리스트 처리
        List<QuestionDto> questionList;
        String questionsJson = analysis.getExpectedQuestionsJson();
        if (questionsJson != null && !questionsJson.isBlank() && !questionsJson.equals("null")) {
            questionList = objectMapper.readValue(questionsJson, new TypeReference<>() {});
        } else {
            questionList = List.of();
        }

        // 점수 데이터 처리
        Map<String, Integer> scoreMap;
        String scoreJson = analysis.getScoreJson();
        if (scoreJson != null && !scoreJson.isBlank() && !scoreJson.equals("null")) {
            scoreMap = objectMapper.readValue(scoreJson, new TypeReference<>() {});
        } else {
            scoreMap = Map.of();
        }

        // 차트 데이터 변환
        List<ChartDataDto> chartDataList = scoreMap.entrySet().stream()
                .map(entry -> ChartDataDto.builder()
                        .subject(entry.getKey())
                        .score(entry.getValue())
                        .fullMark(100)
                        .build())
                .collect(Collectors.toList());

        // 최종 객체 생성
        return PortfolioAnalysisResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .portfolioId(analysis.getTargetId())
                .targetJob(analysis.getTargetJob() != null ? analysis.getTargetJob() : "미설정 직무")
                .overallScore(analysis.getOverallScore())
                .oneLineReview(analysis.getOneLineReview() != null ? analysis.getOneLineReview() : "분석 총평이 없습니다.")
                .summaryDetail(analysis.getSummaryDetail() != null ? analysis.getSummaryDetail() : "상세 분석 내용이 없습니다.")
                .chartData(chartDataList)
                .questions(questionList)
                .nickname(nickname)
                .build();
    }

    /* 포트폴리오 분석 기록 삭제 */
    @Transactional
    public void deletePortfolioAnalysis(Long analysisId, String loginId) {

        // 요청한 유저 확인
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("해당 아이디의 회원을 찾을 수 없습니다: " + loginId));
        Long userNum = member.getUserNum();

        // 삭제할 분석 기록 찾기 및 권한 검증
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("삭제할 분석 결과를 찾을 수 없습니다."));

        if (!analysis.getUserNum().equals(userNum)) {
            throw new RuntimeException("해당 결과에 대한 삭제 권한이 없습니다.");
        }

        if (!"PORTFOLIO".equals(analysis.getTargetType())) {
            throw new RuntimeException("포트폴리오 분석 결과가 아닙니다.");
        }

        Long portfolioId = analysis.getTargetId();

        // 연결된 포트폴리오 찾기
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId).orElse(null);

        if (portfolio != null) {
            Long fileId = portfolio.getFileId();
            FileEntity fileEntity = fileRepository.findById(fileId).orElse(null);

            if (fileEntity != null) {
                String s3Key = fileEntity.getS3Key();
                try {
                    s3Service.deleteFile(s3Key);
                } catch (Exception e) {
                    log.error("S3 파일 삭제 실패... (계속 진행)", e);
                }
            }

            portfolioRepository.delete(portfolio);

            if (fileEntity != null) {
                fileRepository.delete(fileEntity);
            }
        }

        analysisRepository.delete(analysis);
    }
}