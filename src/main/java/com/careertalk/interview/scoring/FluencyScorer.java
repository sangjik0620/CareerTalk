package com.careertalk.interview.scoring;

import com.careertalk.interview.scoring.dto.FluencyScoreResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class FluencyScorer {

    private static final String VERSION = "FL-1.0";

    // weights
    private static final double W_RATE = 0.45;
    private static final double W_SIL  = 0.35;
    private static final double W_LEN  = 0.20;

    public FluencyScoreResult score(
            Double speechRateWps,
            Double silenceRatio,
            Double durationSec,
            Integer wordCount
    ) {
        double rate = nvl(speechRateWps);
        double sil  = nvl(silenceRatio);
        double dur  = nvl(durationSec);
        int wc = wordCount == null ? 0 : wordCount;

        // rate: 적정 2.0~3.7, outer 1.0~5.2
        double cRate = ScoreNormalizer.bandPass(rate, 2.0, 3.7, 1.0, 5.2);

        // silence: 0.10 이하면 좋고 0.55 이상이면 나쁨
        double cSilence = 1.0 - ScoreNormalizer.linearClamp(sil, 0.10, 0.55);

        // length/coverage: 너무 짧으면 유창성 판단이 어려움(가볍게 반영)
        // dur 3초->0.3, 8초->1.0
        double cLen = ScoreNormalizer.linearClamp(dur, 3.0, 8.0);

        double weighted01 = ScoreNormalizer.clamp01(
                W_RATE * cRate +
                        W_SIL  * cSilence +
                        W_LEN  * cLen
        );

        int score = ScoreNormalizer.clampInt((int)Math.round(100 * weighted01), 0, 100);

        // reliability: dur 너무 짧으면 낮춤 + 단어가 너무 적으면 낮춤
        double reliability = 1.0;
        if (dur < 3.0) reliability *= 0.5;
        else if (dur < 6.0) reliability *= 0.7;

        if (wc < 5) reliability *= 0.6;
        else if (wc < 12) reliability *= 0.8;

        reliability = ScoreNormalizer.clamp01(reliability);

        Map<String, Double> comp = new LinkedHashMap<>();
        comp.put("cRate", ScoreNormalizer.round2(cRate));
        comp.put("cSilence", ScoreNormalizer.round2(cSilence));
        comp.put("cLen", ScoreNormalizer.round2(cLen));
        comp.put("weightedFluency01", ScoreNormalizer.round2(weighted01));

        return FluencyScoreResult.builder()
                .fluencyScore(score)
                .analysisReliability(ScoreNormalizer.round2(reliability))
                .components(comp)
                .version(VERSION)
                .build();
    }

    private double nvl(Double v) {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return v;
    }
}