package com.careertalk.interview.service;

import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3PresignedUrlService;
import com.careertalk.file.s3.S3Uploader;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.careertalk.interview.entity.InterviewEvaluation;
import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.entity.InterviewSessionTarget;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewEvaluationRepository;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.interview.repository.InterviewSessionTargetRepository;
import com.careertalk.interview.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.careertalk.interview.dto.SessionTargetRequest;
import com.careertalk.file.service.S3Service;
import com.careertalk.file.entity.FileEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final FileRepository fileRepository;
    private final S3Uploader s3Uploader;
    private final S3PresignedUrlService presignedUrlService;
    private final InterviewSessionTargetRepository sessionTargetRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final S3Service s3Service;

    @Transactional
    public Long saveInterviewVoice(
            Long userId,
            List<MultipartFile> files,
            List<String> questions,
            int durationSec,
            int questionCount,
            String jobCategory
    ) throws IOException {

        if (questionCount <= 0) throw new IllegalArgumentException("questionCount must be > 0");
        if (questions == null || questions.size() != questionCount)
            throw new IllegalArgumentException("questions size must match questionCount");
        if (files == null || files.size() != questionCount)
            throw new IllegalArgumentException("files size must match questionCount");

        InterviewSession session = new InterviewSession();
        session.setUserNum(userId);

        session.setTitle("AI 모의면접");

        session.setJobCategory(
                jobCategory == null || jobCategory.isBlank() ? null : jobCategory.trim()
        );
        session.setMode("VOICE");
        session.setStatus("ENDED");
        session = sessionRepository.save(session);
        String keyPrefix = "interview/" + session.getSessionId();

        for (int i = 0; i < questionCount; i++) {
            MultipartFile audio = files.get(i);

            Long audioFileId = null;

            if (audio != null && !audio.isEmpty()) {
                S3Uploader.UploadResult up = s3Uploader.upload(audio, keyPrefix);

                FileEntity f = new FileEntity();
                f.setUserNum(userId);
                f.setFileType("AUDIO");
                f.setOriginalName(audio.getOriginalFilename() == null ? "answer.webm" : audio.getOriginalFilename());
                f.setMimeType(audio.getContentType() == null ? "audio/webm" : audio.getContentType());
                f.setFileSize(audio.getSize());
                f.setS3Bucket(up.bucket());
                f.setS3Key(up.s3Key());
                f.setS3KeyHash(sha256Bytes(up.bucket() + ":" + up.s3Key()));
                f.setFileUrl(null);
                f.setStatus("ACTIVE");

                f = fileRepository.save(f);
                audioFileId = f.getFileId();
            }

            InterviewTurn turn = new InterviewTurn();
            turn.setSessionId(session.getSessionId());
            turn.setTurnNo(i + 1);
            turn.setAiQuestion(questions.get(i));
            turn.setAnswerAudioFileId(audioFileId);

            turnRepository.save(turn);
        }

        return session.getSessionId();
    }

    @Transactional(readOnly = true)
    public InterviewSessionResultResponse getVoiceResult(Long sessionId, Long userNum) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        if (!session.getUserNum().equals(userNum)) {
            throw new RuntimeException("해당 면접 기록에 접근할 권한이 없습니다.");
        }

        InterviewEvaluation evaluation = evaluationRepository.findBySessionId(sessionId)
                .orElse(null);

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        List<Long> fileIds = turns.stream()
                .map(InterviewTurn::getAnswerAudioFileId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();

        java.util.Map<Long, FileEntity> fileMap = fileIds.isEmpty()
                ? java.util.Map.of()
                : fileRepository.findAllById(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileEntity::getFileId, f -> f));

        List<InterviewSessionResultResponse.TurnItem> items = turns.stream().map(t -> {
            String url = null;
            Long fid = t.getAnswerAudioFileId();
            if (fid != null) {
                FileEntity f = fileMap.get(fid);
                if (f != null && "ACTIVE".equals(f.getStatus())) {
                    url = presignedUrlService.presignGetUrl(f.getS3Bucket(), f.getS3Key(), java.time.Duration.ofMinutes(10));
                }
            }
            return new InterviewSessionResultResponse.TurnItem(t.getTurnNo(), t.getAiQuestion(), url);
        }).toList();

        return InterviewSessionResultResponse.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .status(session.getStatus())
                .mode(session.getMode())
                .overallScore(evaluation != null ? evaluation.getOverallScore() : 0)
                .strengths(evaluation != null ? evaluation.getStrengths() : "분석 데이터가 없습니다.")
                .weaknesses(evaluation != null ? evaluation.getWeaknesses() : "-")
                .nextActions(evaluation != null ? evaluation.getNextActions() : "-")
                .turns(items)
                .build();
    }

    @Transactional
    public void saveSessionTargets(Long sessionId, List<SessionTargetRequest> targets) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId is null");
        if (targets == null || targets.isEmpty()) return;

        for (SessionTargetRequest t : targets) {
            if (t == null) continue;
            if (t.getTargetType() == null || t.getTargetId() == null) continue;

            if (t.getAnalysisId() == null) {
                throw new IllegalArgumentException("analysisId is required for targetType=" + t.getTargetType());
            }

            var existingOpt = sessionTargetRepository.findBySessionIdAndTargetType(sessionId, t.getTargetType());
            if (existingOpt.isPresent()) {
                var existing = existingOpt.get();
                existing.setTargetId(t.getTargetId());
                existing.setAnalysisId(t.getAnalysisId());
                sessionTargetRepository.save(existing);
            } else {
                var entity = new InterviewSessionTarget(
                        sessionId,
                        t.getTargetType(),
                        t.getTargetId(),
                        t.getAnalysisId()
                );
                sessionTargetRepository.save(entity);
            }
        }
    }

    private static byte[] sha256Bytes(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(v.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void deleteInterviewSession(Long sessionId, Long userNum) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("면접 기록을 찾을 수 없습니다."));

        if (!Objects.equals(session.getUserNum(), userNum)) {
            throw new IllegalStateException("해당 면접 기록을 삭제할 권한이 없습니다.");
        }

        List<Long> audioFileIds = turnRepository.findAudioFileIdsBySessionId(sessionId).stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<FileEntity> audioFiles = audioFileIds.isEmpty()
                ? List.of()
                : fileRepository.findAllById(audioFileIds);

        for (FileEntity file : audioFiles) {
            if (file == null) continue;
            if (file.getS3Key() == null || file.getS3Key().isBlank()) continue;

            s3Service.deleteFile(file.getS3Key());
        }

        evaluationRepository.deleteBySessionId(sessionId);
        sessionTargetRepository.deleteBySessionId(sessionId);
        turnRepository.deleteBySessionId(sessionId);

        if (!audioFileIds.isEmpty()) {
            fileRepository.deleteAllByIdInBatch(audioFileIds);
        }

        sessionRepository.deleteBySessionId(sessionId);
    }
}