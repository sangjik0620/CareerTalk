package com.careertalk.interview.service;

import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3Uploader;
import com.careertalk.interview.entity.InterviewSession;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.interview.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
        session.setTitle("AI 모의면접");

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

    private static byte[] sha256Bytes(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(v.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}