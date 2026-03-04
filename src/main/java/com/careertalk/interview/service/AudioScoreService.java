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
            // python_metrics_json 포맷 호환:
            // 1) wrapper: { version, extracted:{...}, raw:{...} }
            // 2) flat: { pitchMean, pitchStd, pitchCv, jitterLocal, shimmerLocal, ... }
            // 3) raw nested: { pitch:{pitchMeanHz,...}, voiceQuality:{...}, durationSec }
            JsonNode extractedNode = (pyNode != null) ? pyNode.get("extracted") : null;
            JsonNode rawNode = (pyNode != null) ? pyNode.get("raw") : null;

            // 1) extracted 우선
            Double pitchMean     = getDouble(extractedNode, "pitchMean");
            Double pitchStd      = getDouble(extractedNode, "pitchStd");
            Double pitchCv       = getDouble(extractedNode, "pitchCv");
            Double jitterLocal   = getDouble(extractedNode, "jitterLocal");
            Double shimmerLocal  = getDouble(extractedNode, "shimmerLocal");

            // 2) flat fallback
            if (pitchMean == null) pitchMean = getDouble(pyNode, "pitchMean");
            if (pitchStd  == null) pitchStd  = getDouble(pyNode, "pitchStd");
            if (pitchCv   == null) pitchCv   = getDouble(pyNode, "pitchCv");
            if (jitterLocal == null) jitterLocal = getDouble(pyNode, "jitterLocal");
            if (shimmerLocal == null) shimmerLocal = getDouble(pyNode, "shimmerLocal");

            // 3) raw nested fallback (FastAPI 원본)
            if (pitchMean == null) pitchMean = getDoubleNested(rawNode, "pitch", "pitchMeanHz");
            if (pitchStd  == null) pitchStd  = getDoubleNested(rawNode, "pitch", "pitchStdHz");
            if (pitchCv   == null) pitchCv   = getDoubleNested(rawNode, "pitch", "pitchCv");
            if (jitterLocal == null) jitterLocal = getDoubleNested(rawNode, "voiceQuality", "jitterLocal");
            if (shimmerLocal == null) shimmerLocal = getDoubleNested(rawNode, "voiceQuality", "shimmerLocal");

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
            OverallVoiceScoreResult overall = overallVoiceScorer.score(
                    tremor.getTremorRiskScore(),
                    tremor.getAnalysisReliability(),
                    confidence.getConfidenceScore(),
                    confidence.getAnalysisReliability()
            );

            // ===== 5) Feedback =====
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