package com.careertalk.interview.scoring;

import com.careertalk.interview.scoring.dto.OverallVoiceScoreResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class OverallVoiceScorer {

    private static final String VERSION = "OV-1.0";

    private static final double W_CONF = 0.55;
    private static final double W_STAB = 0.45;

    public OverallVoiceScoreResult score(
            Integer tremorRiskScore,
            Double tremorReliability,
            Integer confidenceScore,
            Double confidenceReliability
    ) {
        int tremor = tremorRiskScore == null ? 50 : tremorRiskScore;
        int conf   = confidenceScore == null ? 50 : confidenceScore;

        double r1 = tremorReliability == null ? 0.7 : tremorReliability;
        double r2 = confidenceReliability == null ? 0.7 : confidenceReliability;

        double overallReliability = Math.max(0.0, Math.min(1.0, Math.min(r1, r2)));

        double stabilityScore = clamp01((100.0 - tremor) / 100.0) * 100.0;

        double raw = W_CONF * conf + W_STAB * stabilityScore;
        double applied = raw * overallReliability;

        int overall = clampInt((int) Math.round(applied), 0, 100);

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("confidenceScore", round2((double) conf));
        components.put("stabilityScore", round2(stabilityScore));
        components.put("overallScoreRaw", round2(raw));
        components.put("overallScoreApplied", round2(applied));

        return OverallVoiceScoreResult.builder()
                .overallVoiceScore(overall)
                .overallReliability(round2(overallReliability))
                .components(components)
                .grade(grade(overall, overallReliability))
                .version(VERSION)
                .build();
    }

    private String grade(int score, double reliability) {
        int s = score;
        if (reliability < 0.55) s -= 8;

        if (s >= 80) return "EXCELLENT";
        if (s >= 65) return "GOOD";
        if (s >= 50) return "FAIR";
        return "NEEDS_WORK";
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}