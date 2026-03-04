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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

        InterviewTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));

        if (turn.getAnswerAudioFileId() == null) {
            return toResponse(turn, "answer_audio_file_id is null", null);
        }

        // ✅ 이미 파이프라인 끝났으면 스킵 (force=false)
        if (!req.isForce()
                && turn.getSttStatus() == SttStatus.SUCCESS
                && turn.getTurnAnalysisStatus() == TurnAnalysisStatus.DONE
                && turn.getTurnScoreStatus() == TurnScoreStatus.DONE) {
            return toResponse(turn, null, nextPath(turn.getSessionId()));
        }

        // ✅ 동시 실행 방지
        if (turn.getSttStatus() == SttStatus.PROCESSING
                || turn.getTurnAnalysisStatus() == TurnAnalysisStatus.PROCESSING
                || turn.getTurnScoreStatus() == TurnScoreStatus.PROCESSING) {
            return toResponse(turn, "Turn pipeline is already processing", null);
        }

        Path tempWebm = null;
        Path tempWav = null;

        try {
            // 0) 파일 조회 + 다운로드
            FileEntity audioFile = fileRepository.findById(turn.getAnswerAudioFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Audio file not found: " + turn.getAnswerAudioFileId()));

            tempWebm = s3DownloadService.downloadToTempFile(audioFile.getS3Key(), audioFile.getOriginalName());

            Path target = tempWebm;
            if (req.isToWav()) {
                tempWav = ffmpegConvertService.toWav16kMono(tempWebm);
                target = tempWav;
            }

            // 1) STT
            runSttStep(turn, target, req);

            // 2) Turn Analysis (FastAPI + 기본지표 저장)
            runTurnAnalysisStep(turn, target, req);

            // 3) Turn Score (Java scoring)
            runTurnScoreStep(turn, req);

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

        turn.setTurnAnalysisStatus(TurnAnalysisStatus.PROCESSING);
        turn.setTurnAnalysisErrorMessage(null);
        turn.setTurnAnalysisAttemptCount(turn.getTurnAnalysisAttemptCount() + 1);
        turn.setTurnAnalysisStartedAt(LocalDateTime.now());
        turn.setTurnAnalysisCompletedAt(null);
        turnRepository.save(turn);

        try {
            // 기본 지표
            double durationSec = audioFeatureExtractor.getDurationSeconds(target);
            int durationInt = (int) Math.round(durationSec);

            String sttText = turn.getSttText();
            int wordCount = (sttText == null || sttText.trim().isBlank())
                    ? 0 : sttText.trim().split("\\s+").length;

            double wps = durationSec > 0 ? (wordCount / durationSec) : 0.0;

            Double meanDb = audioFeatureExtractor.getMeanVolumeDb(target);
            Double silenceRatio = audioFeatureExtractor.getSilenceRatio(target);

            // FastAPI 분석 (wav일 때만 수행)
            JsonNode pyRaw = null;
            if (req.isToWav()) {
                pyRaw = pythonAudioAnalysisClient.analyzeWav(target);
            }

            // audio_metrics_json: 기본 지표 저장
            Map<String, Object> audioMetrics = new LinkedHashMap<>();
            audioMetrics.put("durationSec", durationSec);
            audioMetrics.put("wordCount", wordCount);
            audioMetrics.put("speechRateWps", wps);
            audioMetrics.put("meanVolumeDb", meanDb);
            audioMetrics.put("silenceRatio", silenceRatio);

            // python_metrics_json: extracted + raw(권장)
            Map<String, Object> pythonMetricsWrapper = null;

            if (pyRaw != null && !pyRaw.isNull()) {

                // 1) FastAPI 응답이 "raw 자체가 wrapper"일 수도 있고 (이미 raw/extracted/version이 있을 수도 있음)
                //    보통은 raw가 곧 pitch/voiceQuality 구조임.
                //    여기서는 "pyRaw가 pitch를 직접 들고 있다"는 전제 + fallback까지 지원.

                // ✅ pitch 경로 후보들 (FastAPI 버전/키가 바뀌어도 최대한 살아남게)
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

                // ✅ voiceQuality 경로 후보
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

                // ✅ 스키마 고정을 위한 wrapper
                pythonMetricsWrapper = new LinkedHashMap<>();
                pythonMetricsWrapper.put("schema", "careertalk.python_metrics.v1");
                pythonMetricsWrapper.put("version", "PYMET-1.0");
                pythonMetricsWrapper.put("extracted", extracted);

                // ✅ 원본은 그대로 저장 (디버깅/회귀 분석에 매우 유리)
                pythonMetricsWrapper.put("raw", objectMapper.convertValue(pyRaw, Map.class));
            }

            // turn 저장
            turn.setAnswerAudioDurationSec(durationInt);
            turn.setAudioMetricsJson(objectMapper.writeValueAsString(audioMetrics));
            turn.setPythonMetricsJson(pythonMetricsWrapper == null ? null : objectMapper.writeValueAsString(pythonMetricsWrapper));

            turn.setTurnAnalysisStatus(TurnAnalysisStatus.DONE);
            turn.setTurnAnalysisCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);

        } catch (Exception e) {
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
}