package com.careertalk.interview.service;

import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.scoring.ConfidenceScorer;
import com.careertalk.interview.scoring.OverallVoiceScorer;
import com.careertalk.interview.scoring.TremorRiskScorer;
import com.careertalk.interview.scoring.dto.ConfidenceScoreResult;
import com.careertalk.interview.scoring.dto.OverallVoiceScoreResult;
import com.careertalk.interview.scoring.dto.TremorScoreResult;
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
    private final OverallVoiceScorer overallVoiceScorer = new OverallVoiceScorer();

    public String buildAudioScoresJson(InterviewTurn turn) {

        try {
            if (turn.getAudioMetricsJson() == null ||
                    turn.getPythonMetricsJson() == null) {
                return null;
            }

            JsonNode audioNode = objectMapper.readTree(turn.getAudioMetricsJson());
            JsonNode pyNode    = objectMapper.readTree(turn.getPythonMetricsJson());

            // ===== 공통 값 =====
            Double durationSec   = getDouble(audioNode, "durationSec");
            Double silenceRatio  = getDouble(audioNode, "silenceRatio");
            Double speechRateWps = getDouble(audioNode, "speechRateWps");
            Double meanVolumeDb  = getDouble(audioNode, "meanVolumeDb");

            Double pitchMean     = getDouble(pyNode, "pitchMean");
            Double pitchStd      = getDouble(pyNode, "pitchStd");
            Double pitchCv       = getDouble(pyNode, "pitchCv");
            Double jitterLocal   = getDouble(pyNode, "jitterLocal");
            Double shimmerLocal  = getDouble(pyNode, "shimmerLocal");

            // ===== 1️⃣ Tremor 점수 =====
            TremorScoreResult tremor = tremorRiskScorer.score(
                    jitterLocal,
                    shimmerLocal,
                    pitchMean,
                    pitchStd,
                    pitchCv,
                    silenceRatio,
                    durationSec
            );

            // ===== 2️⃣ Confidence 점수 =====
            ConfidenceScoreResult confidence = confidenceScorer.score(
                    meanVolumeDb,
                    silenceRatio,
                    speechRateWps,
                    pitchCv,
                    jitterLocal,
                    shimmerLocal,
                    durationSec
            );

            // ===== 3️⃣ Overall 점수 =====
            OverallVoiceScoreResult overall = overallVoiceScorer.score(
                    tremor.getTremorRiskScore(),
                    tremor.getAnalysisReliability(),
                    confidence.getConfidenceScore(),
                    confidence.getAnalysisReliability()
            );

            // ===== 4️⃣ 하나의 JSON으로 합치기 =====
            Map<String, Object> finalScores = new LinkedHashMap<>();
            finalScores.put("tremor", tremor);
            finalScores.put("confidence", confidence);
            finalScores.put("overall", overall);

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
}