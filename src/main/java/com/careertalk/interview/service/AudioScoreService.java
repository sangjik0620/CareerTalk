package com.careertalk.interview.service;

import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.scoring.*;
import com.careertalk.interview.scoring.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AudioScoreService {

    private final ObjectMapper objectMapper;

    private final TremorRiskScorer tremorRiskScorer = new TremorRiskScorer();
    private final ConfidenceScorer confidenceScorer = new ConfidenceScorer();
    private final FluencyScorer fluencyScorer = new FluencyScorer();
    private final OverallVoiceScorer overallVoiceScorer = new OverallVoiceScorer();
    private final VoiceFeedbackGenerator voiceFeedbackGenerator = new VoiceFeedbackGenerator();

    public String buildAudioScoresJson(InterviewTurn turn) {

        try {
            if (turn.getAudioMetricsJson() == null || turn.getPythonMetricsJson() == null) {
                return null;
            }

            JsonNode audioNode = objectMapper.readTree(turn.getAudioMetricsJson());
            JsonNode pyNode    = objectMapper.readTree(turn.getPythonMetricsJson());

            // ===== audio_metrics_json =====
            Double durationSec   = getDouble(audioNode, "durationSec");
            Double silenceRatio  = getDouble(audioNode, "silenceRatio");
            Double speechRateWps = getDouble(audioNode, "speechRateWps");
            Double meanVolumeDb  = getDouble(audioNode, "meanVolumeDb");
            Integer wordCount    = getInt(audioNode, "wordCount");

            // ===== python_metrics_json =====
            // ✅ 1) python_metrics_json이 flat 구조인 경우 (추천)
            Double pitchMean     = getDouble(pyNode, "pitchMean");
            Double pitchStd      = getDouble(pyNode, "pitchStd");
            Double pitchCv       = getDouble(pyNode, "pitchCv");
            Double jitterLocal   = getDouble(pyNode, "jitterLocal");
            Double shimmerLocal  = getDouble(pyNode, "shimmerLocal");

            // ✅ 2) python_metrics_json이 nested 구조인 경우(네가 FastAPI raw 응답처럼 저장한 경우)
            //    pitch: { pitchMeanHz, pitchStdHz, pitchCv }, voiceQuality: { jitterLocal, shimmerLocal }
            if (pitchMean == null) pitchMean = getDoubleNested(pyNode, "pitch", "pitchMeanHz");
            if (pitchStd  == null) pitchStd  = getDoubleNested(pyNode, "pitch", "pitchStdHz");
            if (pitchCv   == null) pitchCv   = getDoubleNested(pyNode, "pitch", "pitchCv");
            if (jitterLocal == null) jitterLocal = getDoubleNested(pyNode, "voiceQuality", "jitterLocal");
            if (shimmerLocal == null) shimmerLocal = getDoubleNested(pyNode, "voiceQuality", "shimmerLocal");

            // ===== 1) Tremor =====
            TremorScoreResult tremor = tremorRiskScorer.score(
                    jitterLocal,
                    shimmerLocal,
                    pitchMean,
                    pitchStd,
                    pitchCv,
                    silenceRatio,
                    durationSec
            );

            // ===== 2) Confidence =====
            ConfidenceScoreResult confidence = confidenceScorer.score(
                    meanVolumeDb,
                    silenceRatio,
                    speechRateWps,
                    pitchCv,
                    jitterLocal,
                    shimmerLocal,
                    durationSec
            );

            // ===== 3) Fluency =====
            FluencyScoreResult fluency = fluencyScorer.score(
                    speechRateWps,
                    silenceRatio,
                    durationSec,
                    wordCount
            );

            // ===== 4) Overall =====
            // overall은 tremor + confidence 기반으로 계산 (원하면 fluency 반영 버전도 가능)
            OverallVoiceScoreResult overall = overallVoiceScorer.score(
                    tremor.getTremorRiskScore(),
                    tremor.getAnalysisReliability(),
                    confidence.getConfidenceScore(),
                    confidence.getAnalysisReliability()
            );

            // ===== 5) Feedback (overall + 각 점수 기반 자동 문장 생성) =====
            VoiceFeedbackResult feedback = voiceFeedbackGenerator.generate(
                    overall.getOverallVoiceScore(),
                    overall.getGrade(),
                    overall.getOverallReliability(),
                    tremor.getTremorRiskScore(),
                    confidence.getConfidenceScore(),
                    fluency.getFluencyScore()
            );

            // ===== 최종 JSON =====
            Map<String, Object> finalScores = new LinkedHashMap<>();
            finalScores.put("tremor", tremor);
            finalScores.put("confidence", confidence);
            finalScores.put("fluency", fluency);
            finalScores.put("overall", overall);
            finalScores.put("feedback", feedback);

            return objectMapper.writeValueAsString(finalScores);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to build audio_scores_json", e);
        }
    }

    private Double getDouble(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asDouble();
    }

    private Integer getInt(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asInt();
    }

    private Double getDoubleNested(JsonNode node, String parent, String child) {
        if (node == null || node.isNull()) return null;
        JsonNode p = node.get(parent);
        if (p == null || p.isNull()) return null;
        JsonNode c = p.get(child);
        if (c == null || c.isNull()) return null;
        return c.asDouble();
    }
}