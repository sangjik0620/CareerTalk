package com.careertalk.interview.scoring;

import com.careertalk.interview.scoring.dto.ConfidenceScoreResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfidenceScorer {

    private static final String VERSION = "CF-1.0";

    // 가중치
    private static final double W_VOL  = 0.25;
    private static final double W_SIL  = 0.30;
    private static final double W_RATE = 0.25;
    private static final double W_STAB = 0.20;

    public ConfidenceScoreResult score(
            Double meanVolumeDb,
            Double silenceRatio,
            Double speechRateWps,
            Double pitchCv,
            Double jitterLocal,
            Double shimmerLocal,
            Double durationSec
    ) {

        double vol  = nvl(meanVolumeDb);
        double sil  = nvl(silenceRatio);
        double rate = nvl(speechRateWps);

        // 1️⃣ 음량 (-35dB ~ -20dB 구간)
        double cVolume = ScoreNormalizer.linearClamp(vol, -35.0, -20.0);

        // 2️⃣ 침묵비율 (0.10 이하면 좋고, 0.50 이상이면 나쁨)
        double cSilence = 1.0 - ScoreNormalizer.linearClamp(sil, 0.10, 0.50);

        // 3️⃣ 말속도 (2.0~3.5 WPS 적정)
        double cRate = ScoreNormalizer.bandPass(rate, 2.0, 3.5, 1.0, 5.0);

        // 4️⃣ 안정성 (tremor 요인 반전)
        double rPitch = ScoreNormalizer.piecewiseRisk(nvl(pitchCv),
                new double[]{0.08,0.12,0.18,0.25},
                new double[]{0.10,0.30,0.60,0.80,1.00});

        double rJitter = ScoreNormalizer.piecewiseRisk(nvl(jitterLocal),
                new double[]{0.010,0.020,0.030,0.040},
                new double[]{0.10,0.30,0.60,0.80,1.00});

        double rShimmer = ScoreNormalizer.piecewiseRisk(nvl(shimmerLocal),
                new double[]{0.030,0.050,0.070,0.090},
                new double[]{0.10,0.30,0.60,0.80,1.00});

        double riskStability = ScoreNormalizer.clamp01((rPitch + rJitter + rShimmer) / 3.0);
        double cStability = 1.0 - riskStability;

        double weighted01 = ScoreNormalizer.clamp01(
                W_VOL * cVolume +
                        W_SIL * cSilence +
                        W_RATE * cRate +
                        W_STAB * cStability
        );

        int confidenceScore = ScoreNormalizer.clampInt(
                (int)Math.round(100 * weighted01), 0, 100);

        double reliability = computeReliability(durationSec, silenceRatio);

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("cVolume", ScoreNormalizer.round2(cVolume));
        components.put("cSilence", ScoreNormalizer.round2(cSilence));
        components.put("cRate", ScoreNormalizer.round2(cRate));
        components.put("cStability", ScoreNormalizer.round2(cStability));
        components.put("weightedConfidence01", ScoreNormalizer.round2(weighted01));

        return ConfidenceScoreResult.builder()
                .confidenceScore(confidenceScore)
                .analysisReliability(ScoreNormalizer.round2(reliability))
                .components(components)
                .version(VERSION)
                .build();
    }

    private double computeReliability(Double durationSec, Double silenceRatio) {
        double r = 1.0;

        double dur = nvl(durationSec);
        double sil = nvl(silenceRatio);

        if (dur < 3.0) r *= 0.5;
        else if (dur < 6.0) r *= 0.7;

        if (sil > 0.60) r *= 0.5;
        else if (sil > 0.45) r *= 0.7;

        return ScoreNormalizer.clamp01(r);
    }

    private double nvl(Double v) {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return v;
    }
}