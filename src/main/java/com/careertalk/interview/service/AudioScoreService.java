package com.careertalk.interview.service;

import com.careertalk.interview.entity.InterviewTurn;
import com.careertalk.interview.scoring.TremorRiskScorer;
import com.careertalk.interview.scoring.dto.TremorScoreResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AudioScoreService {

    private final ObjectMapper objectMapper;
    private final TremorRiskScorer tremorRiskScorer = new TremorRiskScorer();

    public String buildAudioScoresJson(InterviewTurn turn) {

        try {
            if (turn.getAudioMetricsJson() == null ||
                    turn.getPythonMetricsJson() == null) {
                return null; // python 분석 안 된 경우
            }

            JsonNode audioNode = objectMapper.readTree(turn.getAudioMetricsJson());
            JsonNode pyNode    = objectMapper.readTree(turn.getPythonMetricsJson());

            Double durationSec  = getDouble(audioNode, "durationSec");
            Double silenceRatio = getDouble(audioNode, "silenceRatio");

            Double pitchMean    = getDouble(pyNode, "pitchMean");
            Double pitchStd     = getDouble(pyNode, "pitchStd");
            Double pitchCv      = getDouble(pyNode, "pitchCv");
            Double jitterLocal  = getDouble(pyNode, "jitterLocal");
            Double shimmerLocal = getDouble(pyNode, "shimmerLocal");

            TremorScoreResult result = tremorRiskScorer.score(
                    jitterLocal,
                    shimmerLocal,
                    pitchMean,
                    pitchStd,
                    pitchCv,
                    silenceRatio,
                    durationSec
            );

            return objectMapper.writeValueAsString(result);

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