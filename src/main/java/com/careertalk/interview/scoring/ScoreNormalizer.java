package com.careertalk.interview.scoring;

public class ScoreNormalizer {

    private ScoreNormalizer() {}

    /**
     * Piecewise risk mapping.
     * thresholds: 오름차순 경계값들 (예: [0.01, 0.02, 0.03, 0.04])
     * levels: 구간별 risk 값들 (thresholds.length + 1)개
     *   예: [0.10, 0.30, 0.60, 0.80, 1.00]
     */
    public static double piecewiseRisk(double x, double[] thresholds, double[] levels) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.5; // 중립값
        if (levels.length != thresholds.length + 1) {
            throw new IllegalArgumentException("levels length must be thresholds length + 1");
        }
        for (int i = 0; i < thresholds.length; i++) {
            if (x <= thresholds[i]) return clamp01(levels[i]);
        }
        return clamp01(levels[levels.length - 1]);
    }

    public static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    public static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}