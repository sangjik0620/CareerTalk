package com.careertalk.interview.service;

import com.careertalk.ai.audio.AudioFeatureExtractor;
import com.careertalk.ai.audio.PythonAudioAnalysisClient;
import com.careertalk.ai.stt.FfmpegConvertService;
import com.careertalk.ai.stt.OpenAiWhisperService;
import com.careertalk.file.entity.FileEntity;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.file.s3.S3DownloadService;
import com.careertalk.interview.dto.TurnSttRequest;
import com.careertalk.interview.dto.TurnSttResponse;
import com.careertalk.interview.entity.*;
import com.careertalk.interview.repository.InterviewTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnSttService {

    private final InterviewTurnRepository turnRepository;
    private final FileRepository fileRepository;

    private final S3DownloadService s3DownloadService;
    private final FfmpegConvertService ffmpegConvertService;
    private final OpenAiWhisperService openAiWhisperService;

    private final AudioFeatureExtractor audioFeatureExtractor;
    private final PythonAudioAnalysisClient pythonAudioAnalysisClient;

    private final AudioScoreService audioScoreService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TurnSttResponse runStt(Long turnId, TurnSttRequest req) throws Exception {
        log.info("[STT-STEP] 1. runStt start turnId={}", turnId);

        InterviewTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));
        log.info("[STT-STEP] 2. turn loaded turnId={}, audioFileId={}", turnId, turn.getAnswerAudioFileId());

        if (turn.getAnswerAudioFileId() == null) {
            log.warn("[STT-STEP] answer_audio_file_id is null turnId={}", turnId);
            return toResponse(turn, "answer_audio_file_id is null", null);
        }

        if (!req.isForce()
                && turn.getSttStatus() == SttStatus.SUCCESS
                && turn.getTurnAnalysisStatus() == TurnAnalysisStatus.DONE
                && turn.getTurnScoreStatus() == TurnScoreStatus.DONE) {
            log.info("[STT-STEP] already completed turnId={}", turnId);
            return toResponse(turn, null, nextPath(turn.getSessionId()));
        }

        if (turn.getSttStatus() == SttStatus.PROCESSING
                || turn.getTurnAnalysisStatus() == TurnAnalysisStatus.PROCESSING
                || turn.getTurnScoreStatus() == TurnScoreStatus.PROCESSING) {
            log.warn("[STT-STEP] already processing turnId={}", turnId);
            return toResponse(turn, "Turn pipeline is already processing", null);
        }

        Path tempWebm = null;
        Path tempWav = null;

        try {
            log.info("[STT-STEP] 3. find audio file start turnId={}", turnId);
            FileEntity audioFile = fileRepository.findById(turn.getAnswerAudioFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Audio file not found: " + turn.getAnswerAudioFileId()));
            log.info("[STT-STEP] 4. find audio file done turnId={}, s3Key={}, originalName={}",
                    turnId, audioFile.getS3Key(), audioFile.getOriginalName());

            log.info("[STT-STEP] 5. s3 download start turnId={}", turnId);
            tempWebm = s3DownloadService.downloadToTempFile(audioFile.getS3Key(), audioFile.getOriginalName());
            log.info("[STT-STEP] 6. s3 download done turnId={}, path={}", turnId, tempWebm);

            Path target = tempWebm;

            if (req.isToWav()) {
                log.info("[STT-STEP] 7. ffmpeg start turnId={}", turnId);
                tempWav = ffmpegConvertService.toWav16kMono(tempWebm);
                log.info("[STT-STEP] 8. ffmpeg done turnId={}, path={}", turnId, tempWav);
                target = tempWav;
            }

            log.info("[STT-STEP] 9. runSttStep start turnId={}", turnId);
            runSttStep(turn, target, req);
            log.info("[STT-STEP] 10. runSttStep done turnId={}", turnId);

            log.info("[STT-STEP] 11. runTurnAnalysisStep start turnId={}", turnId);
            runTurnAnalysisStep(turn, target, req);
            log.info("[STT-STEP] 12. runTurnAnalysisStep done turnId={}", turnId);

            log.info("[STT-STEP] 13. runTurnScoreStep start turnId={}", turnId);
            runTurnScoreStep(turn, req);
            log.info("[STT-STEP] 14. runTurnScoreStep done turnId={}", turnId);

            log.info("[STT-STEP] 15. runStt end turnId={}", turnId);
            return toResponse(turn, null, nextPath(turn.getSessionId()));

        } finally {
            safeDelete(tempWav);
            safeDelete(tempWebm);
        }
    }

    private void runSttStep(InterviewTurn turn, Path target, TurnSttRequest req) throws Exception {
        if (!req.isForce() && turn.getSttStatus() == SttStatus.SUCCESS) return;

        turn.setSttStatus(SttStatus.PROCESSING);
        turn.setSttErrorMessage(null);
        turn.setSttAttemptCount(turn.getSttAttemptCount() + 1);
        turn.setSttStartedAt(LocalDateTime.now());
        turn.setSttCompletedAt(null);
        turnRepository.save(turn);

        try {
            String sttText = openAiWhisperService.transcribe(target);

            turn.setSttText(sttText);
            turn.setSttStatus(SttStatus.SUCCESS);
            turn.setSttCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);

        } catch (Exception e) {
            turn.setSttStatus(SttStatus.FAILED);
            turn.setSttErrorMessage(e.getMessage());
            turn.setSttCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);
            throw e;
        }
    }

    private void runTurnAnalysisStep(InterviewTurn turn, Path target, TurnSttRequest req) throws Exception {
        if (!req.isForce() && turn.getTurnAnalysisStatus() == TurnAnalysisStatus.DONE) return;

        log.info("[TA] 1. start turnId={}", turn.getTurnId());

        turn.setTurnAnalysisStatus(TurnAnalysisStatus.PROCESSING);
        turn.setTurnAnalysisErrorMessage(null);
        turn.setTurnAnalysisAttemptCount(turn.getTurnAnalysisAttemptCount() + 1);
        turn.setTurnAnalysisStartedAt(LocalDateTime.now());
        turn.setTurnAnalysisCompletedAt(null);
        turnRepository.save(turn);
        log.info("[TA] 2. set PROCESSING saved turnId={}", turn.getTurnId());

        try {
            log.info("[TA] 3. duration start turnId={}", turn.getTurnId());
            double durationSec = audioFeatureExtractor.getDurationSeconds(target);
            log.info("[TA] 4. duration done turnId={}, durationSec={}", turn.getTurnId(), durationSec);

            int durationInt = (int) Math.round(durationSec);

            String sttText = turn.getSttText();
            int wordCount = (sttText == null || sttText.trim().isBlank())
                    ? 0 : sttText.trim().split("\\s+").length;

            double wps = durationSec > 0 ? (wordCount / durationSec) : 0.0;

            log.info("[TA] 5. meanVolume start turnId={}", turn.getTurnId());
            Double meanDb = audioFeatureExtractor.getMeanVolumeDb(target);
            log.info("[TA] 6. meanVolume done turnId={}, meanDb={}", turn.getTurnId(), meanDb);

            log.info("[TA] 7. silenceRatio start turnId={}", turn.getTurnId());
            Double silenceRatio = audioFeatureExtractor.getSilenceRatio(target);
            log.info("[TA] 8. silenceRatio done turnId={}, silenceRatio={}", turn.getTurnId(), silenceRatio);

            JsonNode pyRaw = null;
            if (req.isToWav()) {
                log.info("[TA] 9. python analyze start turnId={}, path={}", turn.getTurnId(), target);
                pyRaw = pythonAudioAnalysisClient.analyzeWav(target);
                log.info("[TA] 10. python analyze done turnId={}", turn.getTurnId());
            }

            Map<String, Object> audioMetrics = new LinkedHashMap<>();
            audioMetrics.put("durationSec", durationSec);
            audioMetrics.put("wordCount", wordCount);
            audioMetrics.put("speechRateWps", wps);
            audioMetrics.put("meanVolumeDb", meanDb);
            audioMetrics.put("silenceRatio", silenceRatio);

            Map<String, Object> pythonMetricsWrapper = null;

            if (pyRaw != null && !pyRaw.isNull()) {
                log.info("[TA] 11. python parse start turnId={}", turn.getTurnId());

                Double pitchMean = firstNonNull(
                        getDoublePath(pyRaw, "pitch.pitchMeanHz"),
                        getDoublePath(pyRaw, "pitch.pitchMean"),
                        getDoublePath(pyRaw, "pitch.mean"),
                        getDoublePath(pyRaw, "pitchMeanHz"),
                        getDoublePath(pyRaw, "pitchMean")
                );

                Double pitchStd = firstNonNull(
                        getDoublePath(pyRaw, "pitch.pitchStdHz"),
                        getDoublePath(pyRaw, "pitch.pitchStd"),
                        getDoublePath(pyRaw, "pitch.std"),
                        getDoublePath(pyRaw, "pitchStdHz"),
                        getDoublePath(pyRaw, "pitchStd")
                );

                Double pitchCv = firstNonNull(
                        getDoublePath(pyRaw, "pitch.pitchCv"),
                        getDoublePath(pyRaw, "pitch.cv"),
                        getDoublePath(pyRaw, "pitchCv"),
                        getDoublePath(pyRaw, "pitchCV")
                );

                Double jitterLocal = firstNonNull(
                        getDoublePath(pyRaw, "voiceQuality.jitterLocal"),
                        getDoublePath(pyRaw, "voice_quality.jitterLocal"),
                        getDoublePath(pyRaw, "jitterLocal")
                );

                Double shimmerLocal = firstNonNull(
                        getDoublePath(pyRaw, "voiceQuality.shimmerLocal"),
                        getDoublePath(pyRaw, "voice_quality.shimmerLocal"),
                        getDoublePath(pyRaw, "shimmerLocal")
                );

                Double pyDurationSec = firstNonNull(
                        getDoublePath(pyRaw, "durationSec"),
                        getDoublePath(pyRaw, "duration_sec")
                );

                Map<String, Object> extracted = new LinkedHashMap<>();
                extracted.put("durationSec", pyDurationSec);
                extracted.put("pitchMeanHz", pitchMean);
                extracted.put("pitchStdHz", pitchStd);
                extracted.put("pitchCv", pitchCv);
                extracted.put("jitterLocal", jitterLocal);
                extracted.put("shimmerLocal", shimmerLocal);

                pythonMetricsWrapper = new LinkedHashMap<>();
                pythonMetricsWrapper.put("schema", "careertalk.python_metrics.v1");
                pythonMetricsWrapper.put("version", "PYMET-1.0");
                pythonMetricsWrapper.put("extracted", extracted);
                pythonMetricsWrapper.put("raw", objectMapper.convertValue(pyRaw, Map.class));

                log.info("[TA] 12. python parse done turnId={}", turn.getTurnId());
            }

            turn.setAnswerAudioDurationSec(durationInt);
            turn.setAudioMetricsJson(objectMapper.writeValueAsString(audioMetrics));
            turn.setPythonMetricsJson(
                    pythonMetricsWrapper == null ? null : objectMapper.writeValueAsString(pythonMetricsWrapper)
            );

            log.info("[TA] 13. final save start turnId={}", turn.getTurnId());
            turn.setTurnAnalysisStatus(TurnAnalysisStatus.DONE);
            turn.setTurnAnalysisCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);
            log.info("[TA] 14. final save done turnId={}", turn.getTurnId());

        } catch (Exception e) {
            log.error("[TA] FAILED turnId={}", turn.getTurnId(), e);
            turn.setTurnAnalysisStatus(TurnAnalysisStatus.FAILED);
            turn.setTurnAnalysisErrorMessage(e.getMessage());
            turn.setTurnAnalysisCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);
            throw e;
        }
    }

    private void runTurnScoreStep(InterviewTurn turn, TurnSttRequest req) throws Exception {
        // analysis가 DONE이어야 score 가능
        if (turn.getTurnAnalysisStatus() != TurnAnalysisStatus.DONE) {
            throw new IllegalStateException("Cannot score: turn_analysis_status is not DONE");
        }

        if (!req.isForce() && turn.getTurnScoreStatus() == TurnScoreStatus.DONE) return;

        turn.setTurnScoreStatus(TurnScoreStatus.PROCESSING);
        turn.setTurnScoreErrorMessage(null);
        turn.setTurnScoreAttemptCount(turn.getTurnScoreAttemptCount() + 1);
        turn.setTurnScoreStartedAt(LocalDateTime.now());
        turn.setTurnScoreCompletedAt(null);
        turnRepository.save(turn);

        try {
            String scoresJson = audioScoreService.buildAudioScoresJson(turn);
            turn.setAudioScoresJson(scoresJson);

            turn.setTurnScoreStatus(TurnScoreStatus.DONE);
            turn.setTurnScoreCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);

        } catch (Exception e) {
            turn.setTurnScoreStatus(TurnScoreStatus.FAILED);
            turn.setTurnScoreErrorMessage(e.getMessage());
            turn.setTurnScoreCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);
            throw e;
        }
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
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    private String nextPath(Long sessionId) {
        return "/interview/result?sessionId=" + sessionId;
    }

    private Double getDoublePath(JsonNode root, String path) {
        if (root == null || root.isNull() || path == null || path.isBlank()) return null;

        JsonNode cur = root;
        String[] parts = path.split("\\.");
        for (String p : parts) {
            if (cur == null) return null;
            cur = cur.get(p);
        }
        if (cur == null || cur.isNull()) return null;

        return cur.asDouble();
    }

    private Double firstNonNull(Double... values) {
        if (values == null) return null;
        for (Double v : values) {
            if (v != null) return v;
        }
        return null;
    }
    @Transactional
    public void processSessionTurns(Long sessionId) {
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);

        log.info("[STT] processSessionTurns sessionId={}, turns={}", sessionId, turns.size());

        for (InterviewTurn turn : turns) {
            try {
                log.info("[STT] start turnId={}, turnNo={}", turn.getTurnId(), turn.getTurnNo());

                // 이미 완료된 턴이면 스킵
                if (turn.getSttStatus() == SttStatus.SUCCESS
                        && turn.getTurnAnalysisStatus() == TurnAnalysisStatus.DONE
                        && turn.getTurnScoreStatus() == TurnScoreStatus.DONE) {
                    log.info("[STT] skip already completed turnId={}", turn.getTurnId());
                    continue;
                }

                // 오디오 파일이 없으면 스킵
                if (turn.getAnswerAudioFileId() == null) {
                    log.warn("[STT] skip no answerAudioFileId turnId={}", turn.getTurnId());
                    continue;
                }

                TurnSttRequest req = new TurnSttRequest();
                req.setToWav(true); // Python 음성분석까지 같이 돌릴 거면 true

                runStt(turn.getTurnId(), req);

                log.info("[STT] done turnId={}", turn.getTurnId());

            } catch (Exception e) {
                log.error("[STT] failed turnId={}, sessionId={}", turn.getTurnId(), sessionId, e);
            }
        }
    }
}