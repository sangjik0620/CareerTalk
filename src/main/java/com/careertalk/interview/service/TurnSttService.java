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
import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.entity.SttStatus;
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

    private final AudioScoreService audioScoreService; // ✅ 추가: 점수 계산 서비스
    private final ObjectMapper objectMapper;

    @Transactional
    public TurnSttResponse runStt(Long turnId, TurnSttRequest req) throws Exception {
        // 1️⃣ Turn 조회
        InterviewTurn turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new IllegalArgumentException("InterviewTurn not found: " + turnId));

        if (turn.getAnswerAudioFileId() == null) {
            return toResponse(turn, "answer_audio_file_id is null", null);
        }

        if (turn.getSttStatus() == SttStatus.SUCCESS && !req.isForce()) {
            return toResponse(turn, null, nextPath(turn.getTurnId()));
        }

        if (turn.getSttStatus() == SttStatus.PROCESSING) {
            return toResponse(turn, "STT is already processing", null);
        }

        // 2️⃣ PROCESSING 상태 세팅
        turn.setSttStatus(SttStatus.PROCESSING);
        turn.setSttErrorMessage(null);
        turn.setSttAttemptCount(turn.getSttAttemptCount() + 1);
        turn.setSttStartedAt(LocalDateTime.now());
        turn.setSttCompletedAt(null);
        turnRepository.save(turn);

        Path tempWebm = null;
        Path tempWav = null;

        try {
            // 3️⃣ S3 파일 조회
            FileEntity audioFile = fileRepository.findById(turn.getAnswerAudioFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Audio file not found"));

            // 4️⃣ 다운로드
            tempWebm = s3DownloadService.downloadToTempFile(
                    audioFile.getS3Key(),
                    audioFile.getOriginalName()
            );

            // 5️⃣ WAV 변환 (권장: 항상 true)
            Path target = tempWebm;
            if (req.isToWav()) {
                tempWav = ffmpegConvertService.toWav16kMono(tempWebm);
                target = tempWav;
            }

            // 6️⃣ STT 수행
            String sttText = openAiWhisperService.transcribe(target);

            // 7️⃣ 기본 음성 지표 계산
            double durationSec = audioFeatureExtractor.getDurationSeconds(target);
            int durationInt = (int) Math.round(durationSec);

            int wordCount = sttText == null || sttText.trim().isBlank() ? 0 :
                    sttText.trim().split("\\s+").length;

            double wps = durationSec > 0 ? (wordCount / durationSec) : 0.0;

            Double meanDb = audioFeatureExtractor.getMeanVolumeDb(target);
            Double silenceRatio = audioFeatureExtractor.getSilenceRatio(target);

            // 8️⃣ Python 고급 분석 (원본 JsonNode)
            JsonNode py = null;
            if (req.isToWav()) {
                py = pythonAudioAnalysisClient.analyzeWav(target);
            }

            // 9️⃣ audio_metrics_json (기본 지표만 저장)
            Map<String, Object> audioMetrics = new LinkedHashMap<>();
            audioMetrics.put("durationSec", durationSec);
            audioMetrics.put("wordCount", wordCount);
            audioMetrics.put("speechRateWps", wps);
            audioMetrics.put("meanVolumeDb", meanDb);
            audioMetrics.put("silenceRatio", silenceRatio);

            // ✅ python_metrics_json (점수화에 필요한 값만 뽑아서 저장)
            Map<String, Object> pythonMetrics = null;
            if (py != null && !py.isNull()) {
                pythonMetrics = new LinkedHashMap<>();

                // pitch: mean/std/cv
                // (FastAPI 응답이 pitch 객체 안에 mean/std/cv 형태라고 가정)
                Double pitchMean = getDoublePath(py, "pitch.mean");
                Double pitchStd  = getDoublePath(py, "pitch.std");
                Double pitchCv   = getDoublePath(py, "pitch.cv");

                // voiceQuality: jitterLocal/shimmerLocal
                Double jitterLocal  = getDoublePath(py, "voiceQuality.jitterLocal");
                Double shimmerLocal = getDoublePath(py, "voiceQuality.shimmerLocal");

                // 저장
                pythonMetrics.put("pitchMean", pitchMean);
                pythonMetrics.put("pitchStd", pitchStd);
                pythonMetrics.put("pitchCv", pitchCv);
                pythonMetrics.put("jitterLocal", jitterLocal);
                pythonMetrics.put("shimmerLocal", shimmerLocal);

                Double pyDurationSec = getDoublePath(py, "durationSec");
                pythonMetrics.put("durationSec", pyDurationSec);

                // 필요하면 원본도 함께 저장 가능(옵션)
                // pythonMetrics.put("raw", objectMapper.convertValue(py, Map.class));
            }

            // 🔟 Turn 엔티티 세팅 (저장)
            turn.setAnswerAudioDurationSec(durationInt);

            turn.setAudioMetricsJson(objectMapper.writeValueAsString(audioMetrics));
            turn.setPythonMetricsJson(pythonMetrics == null ? null : objectMapper.writeValueAsString(pythonMetrics));

            turn.setSttText(sttText);
            turn.setSttStatus(SttStatus.SUCCESS);
            turn.setSttCompletedAt(LocalDateTime.now());

            // ✅ 1) tremorRiskScore 계산 → audio_scores_json 저장
            String scoresJson = audioScoreService.buildAudioScoresJson(turn);
            turn.setAudioScoresJson(scoresJson);

            // ✅ save는 한 번만
            turnRepository.save(turn);

            return toResponse(turn, null, nextPath(turn.getTurnId()));

        } catch (Exception e) {

            turn.setSttStatus(SttStatus.FAILED);
            turn.setSttErrorMessage(e.getMessage());
            turn.setSttCompletedAt(LocalDateTime.now());
            turnRepository.save(turn);

            return toResponse(turn, e.getMessage(), null);

        } finally {
            safeDelete(tempWav);
            safeDelete(tempWebm);
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
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {}
    }

    private String nextPath(Long turnId) {
        return "/interview/" + turnId + "/result";
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

        // 숫자 문자열로 올 수도 있으니 asDouble 사용
        return cur.asDouble();
    }
}