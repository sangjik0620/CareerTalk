package com.careertalk.interview.service;

import com.careertalk.ai.audio.AudioFeatureExtractor;
import com.careertalk.ai.stt.FfmpegConvertService;
import com.careertalk.ai.stt.OpenAiWhisperService;
import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3DownloadService;
import com.careertalk.interview.dto.TurnSttRequest;
import com.careertalk.interview.dto.TurnSttResponse;
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.entity.SttStatus;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AudioFeatureExtractor audioFeatureExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public TurnSttResponse runStt(Long turnId, TurnSttRequest req) throws Exception {

        // 1) turn 로드 + 기본 검증
        InterviewTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));

        // 음성 파일 업로드 전
        if (turn.getAnswerAudioFileId() == null) {
            return toResponse(turn, "answer_audio_file_id is null", null);
        }

        // 이미 성공인데 force=false면 그대로 반환(멱등성) + 결과 페이지 이동 경로 제공
        if (turn.getSttStatus() == SttStatus.SUCCESS && !req.isForce()) {
            return toResponse(turn, null, nextPath(turn.getTurnId()));
        }

        // PROCESSING이면 중복 실행 방지
        if (turn.getSttStatus() == SttStatus.PROCESSING) {
            return toResponse(turn, "STT is already processing", null);
        }

        // 2) PROCESSING으로 마킹 (attempt++ 포함)
        markProcessing(turnId);

        Path tempWebm = null;
        Path tempWav = null;

        try {
            // 3) file 조회 → s3_key 확보
            FileEntity audioFile = fileRepository.findById(turn.getAnswerAudioFileId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Audio FileEntity not found: " + turn.getAnswerAudioFileId()
                    ));

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

            // 기존 turn 객체 사용
            double durationSec = audioFeatureExtractor.getDurationSeconds(target);
            int durationInt = (int) Math.round(durationSec);

            int wordCount = sttText.trim().isBlank() ? 0 : sttText.trim().split("\\s+").length;
            double wps = durationSec > 0 ? (wordCount / durationSec) : 0.0;

            Double meanDb = audioFeatureExtractor.getMeanVolumeDb(target);

            // turn 객체에 직접 세팅
            turn.setAnswerAudioDurationSec(durationInt);

            var metrics = new java.util.LinkedHashMap<String, Object>();
            metrics.put("durationSec", durationSec);
            metrics.put("wordCount", wordCount);
            metrics.put("speechRateWps", wps);
            metrics.put("meanVolumeDb", meanDb);

            turn.setAudioMetricsJson(objectMapper.writeValueAsString(metrics));

            // STT 성공 + 저장
            turn.setSttText(sttText);
            turn.setSttStatus(SttStatus.SUCCESS);
            turn.setSttCompletedAt(LocalDateTime.now());

            turnRepository.save(turn);

            return toResponse(turn, null, nextPath(turn.getTurnId()));

        } catch (Exception e) {
            // 8) FAILED 저장 (재시도 가능)
            InterviewTurn failed = markFailed(turnId, e.getMessage());
            return toResponse(failed, e.getMessage(), null);

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

        turnRepository.save(t); // ✅ 권장
    }

    @Transactional
    protected InterviewTurn markSuccess(Long turnId, String sttText) {
        InterviewTurn t = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        t.setSttText(sttText);
        t.setSttStatus(SttStatus.SUCCESS);
        t.setSttErrorMessage(null);
        t.setSttCompletedAt(LocalDateTime.now());
        return turnRepository.save(t);
    }

    @Transactional
    protected InterviewTurn markFailed(Long turnId, String error) {
        InterviewTurn t = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        t.setSttStatus(SttStatus.FAILED);
        t.setSttErrorMessage(error);
        t.setSttCompletedAt(LocalDateTime.now());
        return turnRepository.save(t);
    }

    private TurnSttResponse toResponse(InterviewTurn t, String msg, String nextPath) {
        return TurnSttResponse.builder()
                .turnId(t.getTurnId())
                .sttStatus(t.getSttStatus().name())
                .attemptCount(t.getSttAttemptCount())
                .sttText(t.getSttText())
                .errorMessage(msg != null ? msg : t.getSttErrorMessage())
                .nextPath(nextPath)
                .build();
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    // ✅ STT 완료 후 프론트가 이동할 결과 페이지 라우트
    private String nextPath(Long turnId) {
        return "/interview/" + turnId + "/result";
    }
}