package com.careertalk.interview.service;

import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3DownloadService;
import com.careertalk.ai.stt.FfmpegConvertService;
import com.careertalk.ai.stt.OpenAiWhisperService;
import com.careertalk.interview.dto.TurnSttRequest;
import com.careertalk.interview.dto.TurnSttResponse;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.entity.SttStatus;
import com.careertalk.interview.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TurnSttService {

    private final InterviewTurnRepository turnRepository;
    private final FileRepository fileRepository;

    private final S3DownloadService s3DownloadService;
    private final FfmpegConvertService ffmpegConvertService;
    private final OpenAiWhisperService openAiWhisperService;

    /**
     * turn_id 기준 STT 실행 (재시도 가능)
     */
    public TurnSttResponse runStt(Long turnId, TurnSttRequest req) throws Exception {

        // 1) turn 로드 + 기본 검증
        InterviewTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));

        if (turn.getAnswerAudioFileId() == null) {
            // 음성 파일 업로드 전
            return toResponse(turn, "answer_audio_file_id is null");
        }

        // 이미 성공인데 force=false면 그대로 반환(멱등성)
        if (turn.getSttStatus() == SttStatus.SUCCESS && !req.isForce()) {
            return toResponse(turn, null);
        }

        // PROCESSING이면 중복 실행 방지
        if (turn.getSttStatus() == SttStatus.PROCESSING) {
            return toResponse(turn, "STT is already processing");
        }

        // 2) PROCESSING으로 마킹 (attempt++ 포함)
        markProcessing(turnId);

        Path tempWebm = null;
        Path tempWav = null;

        try {
            // 3) file 조회 → s3_key 확보
            FileEntity audioFile = fileRepository.findById(turn.getAnswerAudioFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Audio FileEntity not found: " + turn.getAnswerAudioFileId()));

            // 4) S3 다운로드 → 임시파일
            tempWebm = s3DownloadService.downloadToTempFile(audioFile.getS3Key(), audioFile.getOriginalName());

            // 5) 옵션 변환
            Path target = tempWebm;
            if (req.isToWav()) {
                tempWav = ffmpegConvertService.toWav16kMono(tempWebm);
                target = tempWav;
            }

            // 6) Whisper 호출
            String sttText = openAiWhisperService.transcribe(target);

            // 7) SUCCESS 저장
            InterviewTurn saved = markSuccess(turnId, sttText);
            return toResponse(saved, null);

        } catch (Exception e) {
            // 8) FAILED 저장 (재시도 가능)
            InterviewTurn failed = markFailed(turnId, e.getMessage());
            return toResponse(failed, e.getMessage());

        } finally {
            safeDelete(tempWav);
            safeDelete(tempWebm);
        }
    }

    @Transactional
    protected void markProcessing(Long turnId) {
        InterviewTurn t = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        t.setSttStatus(SttStatus.PROCESSING);
        t.setSttErrorMessage(null);
        t.setSttAttemptCount(t.getSttAttemptCount() + 1);
        t.setSttStartedAt(LocalDateTime.now());
        t.setSttCompletedAt(null);
    }

    @Transactional
    protected InterviewTurn markSuccess(Long turnId, String sttText) {
        InterviewTurn t = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        t.setSttText(sttText);
        t.setSttStatus(SttStatus.SUCCESS);
        t.setSttErrorMessage(null);
        t.setSttCompletedAt(LocalDateTime.now());
        return t;
    }

    @Transactional
    protected InterviewTurn markFailed(Long turnId, String error) {
        InterviewTurn t = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        t.setSttStatus(SttStatus.FAILED);
        t.setSttErrorMessage(error);
        t.setSttCompletedAt(LocalDateTime.now());
        return t;
    }

    private TurnSttResponse toResponse(InterviewTurn t, String msg) {
        return TurnSttResponse.builder()
                .turnId(t.getTurnId())
                .sttStatus(t.getSttStatus().name())
                .attemptCount(t.getSttAttemptCount())
                .sttText(t.getSttText())
                .errorMessage(msg != null ? msg : t.getSttErrorMessage())
                .build();
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}