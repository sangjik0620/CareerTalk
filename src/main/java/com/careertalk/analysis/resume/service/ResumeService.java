package com.careertalk.analysis.resume.service;

import com.careertalk.analysis.common.entity.AnalysisEntity;
import com.careertalk.analysis.common.repository.AnalysisRepository;
import com.careertalk.analysis.common.service.OpenAiService;
import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse;
import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse.DetailedEvaluationDto;
import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse.EvalItemDto;
import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse.QuestionDto;
import com.careertalk.analysis.resume.entity.ResumeEntity;
import com.careertalk.analysis.resume.repository.ResumeRepository;
import com.careertalk.analysis.resume.util.DocxParsingUtill;
import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.service.S3Service;
import com.careertalk.payment.dto.PaymentQuotaResponse;
import com.careertalk.payment.service.QuotaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB

    private final ResumeRepository resumeRepository;
    private final FileRepository fileRepository;
    private final AnalysisRepository analysisRepository;
    private final S3Service s3Service;
    private final DocxParsingUtill docxParsingUtill;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;

    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    @Value("classpath:prompts/resume-analysis-prompt.txt")
    private Resource systemPromptResource;

    // ⭐ 추가: Portfolio와 동일하게 @Value로 주입
    @Value("${ai.model.name}")
    private String aiModelName;

    @Value("${ai.model.version}")
    private String aiModelVersion;

    @Value("${ai.prompt.version}")
    private String aiPromptVersion;

    // ──────────────────────────────────────────────
    // POST /api/resumes/analyze
    // ──────────────────────────────────────────────
    @Transactional
    public ResumeAnalysisResponse analyzeAndSave(
            MultipartFile file, String jobCategory, String detailedPosition, Long currentUserId) {
        // ① 이용권 확인
        PaymentQuotaResponse quota = quotaService.getQuota(currentUserId);
        if (quota.getFreeAnalysisRemaining() <= 0 && quota.getPaidAnalysisRemaining() <= 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "이용권이 없습니다. 이용권을 구매해 주세요.");
        }

        quotaService.consumeAnalysis(currentUserId, "resumes", null, "이력서 분석 요청");

        // 1. 파일 유효성 검사
        validateFile(file);

        // 2. S3 업로드
        String s3Key;
        try {
            s3Key = s3Service.uploadFile(file, currentUserId);
        } catch (Exception e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }

        // 3. FileEntity 저장
        Long fileId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s3Key.getBytes(StandardCharsets.UTF_8));

            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserNum(currentUserId);
            fileEntity.setFileType("RESUME");
            fileEntity.setOriginalName(file.getOriginalFilename());
            fileEntity.setMimeType(file.getContentType());
            fileEntity.setFileSize(file.getSize());
            fileEntity.setS3Bucket(s3BucketName);
            fileEntity.setS3Key(s3Key);
            fileEntity.setS3KeyHash(hash);
            fileEntity.setStatus("ACTIVE");

            fileId = fileRepository.save(fileEntity).getFileId();
        } catch (Exception e) {
            log.error("FileEntity 저장 실패", e);
            throw new RuntimeException("파일 정보 저장 중 오류가 발생했습니다.");
        }

        // 4. DOCX 텍스트 파싱 (S3에서 바로 스트림)
        String extractedText;
        try (InputStream in = s3Service.downloadFile(s3Key)) {
            extractedText = docxParsingUtill.parseDocx(in);
        } catch (Exception e) {
            log.error("DOCX 파싱 실패: s3Key={}", s3Key, e);
            throw new RuntimeException("DOCX 파싱 중 오류가 발생했습니다.");
        }

        // 5. ResumeEntity 저장
        ResumeEntity resume = ResumeEntity.builder()
                .userNum(currentUserId)
                .fileId(fileId)
                .resumeTitle(file.getOriginalFilename())
                .status("ACTIVE")
                .build();
        resumeRepository.save(resume);

        // 6. 프롬프트 플레이스홀더 치환 후 OpenAI 호출
        String systemPrompt = getSystemPrompt()
                .replace("{{직군}}", jobCategory)
                .replace("{{상세포지션}}", detailedPosition)
                .replace("{{DOCX에서 추출된 텍스트}}", extractedText);

        log.info("AI 이력서 분석 시작... (직군: {}, 포지션: {})", jobCategory, detailedPosition);
        String aiResultJson = openAiService.getAiResponse(systemPrompt, "", null);
        log.info("AI 이력서 분석 완료!");

        // 7. 분석 결과 저장 및 DTO 반환
        return processAndSaveAiResult(
                aiResultJson, currentUserId, resume.getResumeId(), jobCategory, detailedPosition);
    }

    // ──────────────────────────────────────────────
    // GET /api/resumes/{analysisId}/result  ⭐ 추가
    // ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ResumeAnalysisResponse getAnalysisResult(Long analysisId, Long currentUserNum) {
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당 이력서의 분석 결과를 찾을 수 없습니다."));
        // ✅ 본인 소유 확인 (추가)
        if (!analysis.getUserNum().equals(currentUserNum)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "본인의 분석 결과만 조회할 수 있습니다.");
        }

        // ✅ targetType 검증 추가
        if (!"RESUME".equals(analysis.getTargetType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "이력서 분석 결과가 아닙니다.");
        }

        // ✅ FAILED 상태 방어
        if ("FAILED".equals(analysis.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    analysis.getErrorMessage());
        }

        try {
            // detailedPosition은 AnalysisEntity에 별도 저장하지 않으므로 null 전달
            return convertToResponseDto(analysis, null);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 에러", e);
            throw new RuntimeException("분석 결과를 불러오는 중 오류가 발생했습니다.");
        }
    }

    // ──────────────────────────────────────────────
    // 기존: DOCX 텍스트 파싱만 반환
    // ──────────────────────────────────────────────
    public Map<String, Object> parseResumeTextByResumeId(Long resumeId, Long currentUserId) {
        ResumeEntity resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "resumeId에 해당하는 이력서를 찾을 수 없습니다."));

        if (currentUserId != null && !currentUserId.equals(resume.getUserNum())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 이력서만 조회할 수 있습니다.");
        }
        return parseResumeTextByFileId(resume.getFileId(), currentUserId);
    }

    public Map<String, Object> parseResumeTextByFileId(Long fileId, Long currentUserId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "fileId에 해당하는 파일을 찾을 수 없습니다."));

        if (currentUserId != null && !currentUserId.equals(file.getUserNum())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 파일만 조회할 수 있습니다.");
        }
        if (StringUtils.hasText(file.getFileType()) && !"RESUME".equalsIgnoreCase(file.getFileType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RESUME 타입 파일만 파싱할 수 있습니다.");
        }
        if (file.getFileSize() != null && file.getFileSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량은 최대 20MB까지 가능합니다.");
        }
        if (!isDocx(file.getOriginalName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX 파일만 파싱할 수 있습니다.");
        }
        if (!StringUtils.hasText(file.getS3Key())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 key가 비어 있어 파일을 가져올 수 없습니다.");
        }

        try (InputStream in = s3Service.downloadFile(file.getS3Key())) {
            String text = docxParsingUtill.parseDocx(in);
            return Map.of(
                    "ok", true,
                    "fileId", file.getFileId(),
                    "originalName", file.getOriginalName(),
                    "text", text
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("DOCX 파싱 실패: fileId={}, s3Key={}", file.getFileId(), file.getS3Key(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DOCX 파싱 중 오류가 발생했습니다.");
        }
    }

    // ──────────────────────────────────────────────
    // private 공통 메서드
    // ──────────────────────────────────────────────

    /** 프롬프트 파일 로드 */
    private String getSystemPrompt() {
        try {
            return StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("프롬프트 파일 읽기 실패", e);
            throw new RuntimeException("서버 설정 오류로 분석을 시작할 수 없습니다.");
        }
    }

    /** AI 응답 JSON → AnalysisEntity 저장 → ResumeAnalysisResponse 반환 */
    private ResumeAnalysisResponse processAndSaveAiResult(
            String aiResultJson, Long userId, Long resumeId,
            String jobCategory, String detailedPosition) {
        try {
            JsonNode root = objectMapper.readTree(aiResultJson);

            // ✅ 정합성 검증 실패 처리
            if (root.path("validationError").asBoolean(false)) {
                String errorDetail = root.path("errorDetail").asText("직군 정합성 오류");

                AnalysisEntity failed = AnalysisEntity.builder()
                        .userNum(userId)
                        .targetType("RESUME")
                        .targetId(resumeId)
                        .targetJob(jobCategory)
                        .status("FAILED")
                        .errorMessage(errorDetail)
                        .analyzedAt(LocalDateTime.now())
                        .build();

                analysisRepository.save(failed);

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, errorDetail);
            }

            int overallScore             = root.get("overallScore").asInt();
            String summaryDetail         = root.get("summaryDetail").asText();       // 한줄 총평
            String detailedEvalJson      = root.get("detailedEvaluation").toString(); // 항목별 점수 → scoreJson
            String expectedQuestionsJson = root.get("expectedQuestionsJson").toString();
            String feedbackJson          = root.get("feedback").toString();

            AnalysisEntity analysis = AnalysisEntity.builder()
                    .userNum(userId)
                    .targetType("RESUME")
                    .targetId(resumeId)
                    .targetJob(jobCategory)
                    .overallScore(overallScore)
                    .oneLineReview(summaryDetail)   // ⭐ 한줄 총평 → oneLineReview
                    .scoreJson(detailedEvalJson)    // ⭐ 항목별 점수 → scoreJson
                    .ruleResultJson(feedbackJson)   // ⭐ 강점/약점/개선점 묶음 → ruleResultJson
                    .expectedQuestionsJson(expectedQuestionsJson)
                    .status("SUCCESS")
                    .modelName(aiModelName)
                    .modelVersion(aiModelVersion)
                    .promptVersion(aiPromptVersion)
                    .analyzedAt(LocalDateTime.now())
                    .build();

            AnalysisEntity saved = analysisRepository.save(analysis);
            return convertToResponseDto(saved, detailedPosition);

        } catch (Exception e) {
            log.error("AI 응답 결과 처리 중 에러 발생", e);
            throw new RuntimeException("분석 결과를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /** AnalysisEntity → ResumeAnalysisResponse 변환 */
    private ResumeAnalysisResponse convertToResponseDto(
            AnalysisEntity analysis, String detailedPosition) throws JsonProcessingException {

        // ⭐ scoreJson → detailedEvaluation 파싱
        JsonNode evalNode = objectMapper.readTree(analysis.getScoreJson());
        DetailedEvaluationDto detailedEvaluation = DetailedEvaluationDto.builder()
                .jobFitScore(parseEvalItem(evalNode.get("jobFitScore")))
                .experienceScore(parseEvalItem(evalNode.get("experienceScore")))
                .skillScore(parseEvalItem(evalNode.get("skillScore")))
                .growthScore(parseEvalItem(evalNode.get("growthScore")))
                .completenessScore(parseEvalItem(evalNode.get("completenessScore")))
                .build();

        // ⭐ ruleResultJson → feedback 노드에서 바로 꺼내기
        JsonNode feedbackNode = objectMapper.readTree(analysis.getRuleResultJson());
        List<String> strengths    = objectMapper.readValue(feedbackNode.get("strengths").toString(),    new TypeReference<>() {});
        List<String> weaknesses   = objectMapper.readValue(feedbackNode.get("weaknesses").toString(),   new TypeReference<>() {});
        List<String> improvements = objectMapper.readValue(feedbackNode.get("improvements").toString(), new TypeReference<>() {});

        // expectedQuestionsJson 파싱
        List<QuestionDto> questions = objectMapper.readValue(
                analysis.getExpectedQuestionsJson(), new TypeReference<>() {});

        return ResumeAnalysisResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .resumeId(analysis.getTargetId())
                .targetJob(analysis.getTargetJob())
                .detailedPosition(detailedPosition)
                .overallScore(analysis.getOverallScore())
                .summaryDetail(analysis.getOneLineReview()) // ⭐ oneLineReview에서 꺼내서 DTO의 summaryDetail로
                .detailedEvaluation(detailedEvaluation)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .improvements(improvements)
                .expectedQuestionsJson(questions)
                .createdAt(analysis.getAnalyzedAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();
    }

    /** JsonNode 한 항목 → EvalItemDto */
    private EvalItemDto parseEvalItem(JsonNode node) {
        if (node == null) return EvalItemDto.builder().score(0).evaluation("").build();
        return EvalItemDto.builder()
                .score(node.get("score").asInt())
                .evaluation(node.get("evaluation").asText())
                .build();
    }

    /** 파일 유효성 검사 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 없습니다.");
        }
        if (!isDocx(file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX 파일만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량은 최대 20MB까지 가능합니다.");
        }
    }

    private boolean isDocx(String filename) {
        return StringUtils.hasText(filename) && filename.toLowerCase().endsWith(".docx");
    }
}