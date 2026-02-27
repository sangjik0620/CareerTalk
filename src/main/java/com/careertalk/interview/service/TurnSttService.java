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

    private final ObjectMapper objectMapper; // ✅ Spring Bean 주입

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

            int wordCount = sttText.trim().isBlank() ? 0 :
                    sttText.trim().split("\\s+").length;

            double wps = durationSec > 0 ?
                    (wordCount / durationSec) : 0.0;

            Double meanDb = audioFeatureExtractor.getMeanVolumeDb(target);
            Double silenceRatio = audioFeatureExtractor.getSilenceRatio(target);

            // 8️⃣ Python 고급 분석
            JsonNode py = null;
            if (req.isToWav()) {
                py = pythonAudioAnalysisClient.analyzeWav(target);
            }

            // 9️⃣ metrics JSON 구성
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("durationSec", durationSec);
            metrics.put("wordCount", wordCount);
            metrics.put("speechRateWps", wps);
            metrics.put("meanVolumeDb", meanDb);
            metrics.put("silenceRatio", silenceRatio);

            if (py != null) {

                if (py.has("pitch")) {
                    metrics.put("pitch",
                            objectMapper.convertValue(py.get("pitch"), Map.class));
                }

                if (py.has("voiceQuality")) {
                    metrics.put("voiceQuality",
                            objectMapper.convertValue(py.get("voiceQuality"), Map.class));
                }
            }

            // 🔟 Turn 엔티티 세팅
            turn.setAnswerAudioDurationSec(durationInt);
            turn.setAudioMetricsJson(objectMapper.writeValueAsString(metrics));

            turn.setSttText(sttText);
            turn.setSttStatus(SttStatus.SUCCESS);
            turn.setSttCompletedAt(LocalDateTime.now());

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
}