package com.careertalk.interview.scoring;

public class ScoreNormalizer {

    private ScoreNormalizer() {}

    public static double piecewiseRisk(double x, double[] thresholds, double[] levels) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.5;
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

    public static double linearClamp(double x, double min, double max) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.5;
        if (max <= min) return 0.5;
        if (x <= min) return 0.0;
        if (x >= max) return 1.0;
        return (x - min) / (max - min);
    }

    public static double bandPass(double x, double low, double high, double outerLow, double outerHigh) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.5;
        if (outerLow >= low || outerHigh <= high) return 0.5;

        if (x >= low && x <= high) return 1.0;

        if (x < low) {
            return clamp01(linearClamp(x, outerLow, low));
        } else {
            return clamp01(1.0 - linearClamp(x, high, outerHigh));
        }
    }
}