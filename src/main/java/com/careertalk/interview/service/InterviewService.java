package com.careertalk.interview.service;

import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3PresignedUrlService;
import com.careertalk.file.s3.S3Uploader;
import com.careertalk.interview.dto.InterviewSessionResultResponse;
import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.entity.InterviewSessionTarget;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.interview.repository.InterviewSessionTargetRepository;
import com.careertalk.interview.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.careertalk.interview.dto.SessionTargetRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final FileRepository fileRepository;
    private final S3Uploader s3Uploader;
    private final S3PresignedUrlService presignedUrlService;
    private final InterviewSessionTargetRepository sessionTargetRepository;


    @Transactional
    public Long saveInterviewVoice(
            Long userId,
            List<MultipartFile> files,
            List<String> questions,
            int durationSec,
            int questionCount
    ) throws IOException {

        if (questionCount <= 0) throw new IllegalArgumentException("questionCount must be > 0");
        if (questions == null || questions.size() != questionCount)
            throw new IllegalArgumentException("questions size must match questionCount");
        if (files == null || files.size() != questionCount)
            throw new IllegalArgumentException("files size must match questionCount");

        // 1) 세션 생성 (title은 DB DEFAULT)
        // 1) 세션 생성 (title 기본값 세팅)
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);

        // ✅ 추가: title 기본값
        session.setTitle(" AI 모의면접");

        session.setMode("VOICE");
        session.setStatus("ENDED");
        session = sessionRepository.save(session);

        String keyPrefix = "interview/" + session.getSessionId();

        // 2) turns + files 저장
        for (int i = 0; i < questionCount; i++) {
            MultipartFile audio = files.get(i);

            Long audioFileId = null;

            if (audio != null && !audio.isEmpty()) {
                // S3 업로드
                S3Uploader.UploadResult up = s3Uploader.upload(audio, keyPrefix);

                // files insert
                FileEntity f = new FileEntity();
                f.setUserId(userId);
                f.setFileType("AUDIO");
                f.setOriginalName(audio.getOriginalFilename() == null ? "answer.webm" : audio.getOriginalFilename());
                f.setMimeType(audio.getContentType() == null ? "audio/webm" : audio.getContentType());
                f.setFileSize(audio.getSize());
                f.setS3Bucket(up.bucket());
                f.setS3Key(up.s3Key());
                f.setS3KeyHash(sha256Bytes(up.bucket() + ":" + up.s3Key()));
                f.setFileUrl(null); // PRIVATE이면 presigned로 제공
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
    public InterviewSessionResultResponse getVoiceResult(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));

        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        // 1) fileId들 모아서 한번에 조회
        List<Long> fileIds = turns.stream()
                .map(InterviewTurn::getAnswerAudioFileId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();

        // 2) files 한 번에 로딩 → Map으로 변환
        java.util.Map<Long, FileEntity> fileMap = fileIds.isEmpty()
                ? java.util.Map.of()
                : fileRepository.findAllById(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileEntity::getFileId, f -> f));

        // 3) 응답 만들기 (presigned는 여기서만 발급)
        List<InterviewSessionResultResponse.TurnItem> items = turns.stream().map(t -> {
            String url = null;

            Long fid = t.getAnswerAudioFileId();
            if (fid != null) {
                FileEntity f = fileMap.get(fid);
                if (f != null && "ACTIVE".equals(f.getStatus())) { // 소프트삭제 대응 (추천)
                    url = presignedUrlService.presignGetUrl(
                            f.getS3Bucket(),
                            f.getS3Key(),
                            java.time.Duration.ofMinutes(10)
                    );
                }
            }

            return new InterviewSessionResultResponse.TurnItem(
                    t.getTurnNo(),
                    t.getAiQuestion(),
                    url
            );
        }).toList();

        return new InterviewSessionResultResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getStatus(),
                session.getMode(),
                items
        );
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
}