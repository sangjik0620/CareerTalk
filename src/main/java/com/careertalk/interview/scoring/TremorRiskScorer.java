package com.careertalk.interview.scoring;

import com.careertalk.interview.scoring.dto.FlagItem;
import com.careertalk.interview.scoring.dto.TremorScoreResult;

import java.util.*;

import static com.careertalk.interview.scoring.ScoreNormalizer.*;

public class TremorRiskScorer {

    // ----- piecewise 기준 (초기안) -----
    private static final double[] JITTER_T = {0.010, 0.020, 0.030, 0.040};
    private static final double[] JITTER_L = {0.10, 0.30, 0.60, 0.80, 1.00};

    private static final double[] SHIMMER_T = {0.030, 0.050, 0.070, 0.090};
    private static final double[] SHIMMER_L = {0.10, 0.30, 0.60, 0.80, 1.00};

    private static final double[] PITCHCV_T = {0.08, 0.12, 0.18, 0.25};
    private static final double[] PITCHCV_L = {0.10, 0.30, 0.60, 0.80, 1.00};

    private static final double[] SILENCE_T = {0.10, 0.20, 0.35, 0.50};
    private static final double[] SILENCE_L = {0.10, 0.30, 0.60, 0.80, 1.00};

    // ----- 가중치 -----
    private static final double W_JITTER = 0.40;
    private static final double W_SHIMMER = 0.35;
    private static final double W_PITCH   = 0.20;
    private static final double W_SILENCE = 0.05;

    private static final String VERSION = "TR-1.0";

    public TremorScoreResult score(
            Double jitterLocal,
            Double shimmerLocal,
            Double pitchMean,
            Double pitchStd,
            Double pitchCvProvided,   // 있으면 이걸 우선 사용
            Double silenceRatio,
            Double durationSec
    ) {
        // 1) pitchCv 계산 (우선순위: provided > std/mean)
        double pitchCv = computePitchCv(pitchMean, pitchStd, pitchCvProvided);

        // 2) Risk 정규화(0~1)
        double rJitter  = piecewiseRisk(nvl(jitterLocal), JITTER_T, JITTER_L);
        double rShimmer = piecewiseRisk(nvl(shimmerLocal), SHIMMER_T, SHIMMER_L);
        double rPitchCv = piecewiseRisk(pitchCv, PITCHCV_T, PITCHCV_L);
        double rSilence = piecewiseRisk(nvl(silenceRatio), SILENCE_T, SILENCE_L);

        // 3) 가중합
        double weightedRisk01 = clamp01(
                W_JITTER * rJitter +
                        W_SHIMMER * rShimmer +
                        W_PITCH * rPitchCv +
                        W_SILENCE * rSilence
        );

        int tremorRiskScore = clampInt((int) Math.round(100.0 * weightedRisk01), 0, 100);

        // 4) 신뢰도 계산
        double reliability = computeReliability(durationSec, silenceRatio, pitchMean, pitchStd, jitterLocal, shimmerLocal);

        // 5) flags 생성
        List<FlagItem> flags = buildFlags(jitterLocal, shimmerLocal, pitchCv, silenceRatio, durationSec);

        // 6) riskComponents 구성(디버깅/설명용)
        Map<String, Double> components = new LinkedHashMap<>();
        components.put("rJitter", round2(rJitter));
        components.put("rShimmer", round2(rShimmer));
        components.put("rPitchCv", round2(rPitchCv));
        components.put("rSilence", round2(rSilence));
        components.put("weightedRisk01", round2(weightedRisk01));

        return TremorScoreResult.builder()
                .tremorRiskScore(tremorRiskScore)
                .analysisReliability(round2(reliability))
                .riskComponents(components)
                .flags(flags)
                .version(VERSION)
                .build();
    }

    private double computePitchCv(Double pitchMean, Double pitchStd, Double pitchCvProvided) {
        if (pitchCvProvided != null && isFinite(pitchCvProvided)) {
            return Math.max(0.0, pitchCvProvided);
        }
        if (pitchMean == null || pitchStd == null) return 0.12; // 중립값(보수적)
        if (!isFinite(pitchMean) || !isFinite(pitchStd)) return 0.12;
        if (pitchMean <= 1e-9) return 0.12; // mean 0 방지
        double cv = pitchStd / pitchMean;
        if (!isFinite(cv)) return 0.12;
        return Math.max(0.0, cv);
    }

    private double computeReliability(Double durationSec, Double silenceRatio,
                                      Double pitchMean, Double pitchStd,
                                      Double jitterLocal, Double shimmerLocal) {
        double r = 1.0;

        double dur = nvl(durationSec);
        double sil = nvl(silenceRatio);

        // 길이 보정
        if (dur < 3.0) r *= 0.5;
        else if (dur < 6.0) r *= 0.7;

        // 유효 발화 부족(침묵 과다)
        if (sil > 0.60) r *= 0.5;
        else if (sil > 0.45) r *= 0.7;

        // 핵심 지표 누락/비정상일 때
        if (!isFinite(pitchMean) || !isFinite(pitchStd)) r *= 0.8;
        if (!isFinite(jitterLocal) || !isFinite(shimmerLocal)) r *= 0.8;

        return clamp01(r);
    }

    private List<FlagItem> buildFlags(Double jitterLocal, Double shimmerLocal, double pitchCv,
                                      Double silenceRatio, Double durationSec) {
        List<FlagItem> flags = new ArrayList<>();

        double j = nvl(jitterLocal);
        double s = nvl(shimmerLocal);
        double sil = nvl(silenceRatio);
        double dur = nvl(durationSec);

        if (j >= 0.030) flags.add(flag("JITTER_HIGH", "발성이 미세하게 흔들리는 경향이 있어요."));
        if (s >= 0.070) flags.add(flag("SHIMMER_HIGH", "볼륨(진폭) 안정성이 떨어져 떨림처럼 들릴 수 있어요."));
        if (pitchCv >= 0.18) flags.add(flag("PITCH_UNSTABLE", "음높이 변동이 커서 긴장된 인상을 줄 수 있어요."));
        if (sil >= 0.35) flags.add(flag("SILENCE_HIGH", "말 사이 멈춤이 잦아 불안하게 들릴 수 있어요."));
        if (dur > 0 && dur < 6.0) flags.add(flag("LOW_SAMPLE", "분석 구간이 짧아 점수 신뢰도가 낮을 수 있어요."));

        // 파이썬 지표가 0/누락으로 들어오는 경우 감지(필요시 튜닝)
        if (!isFinite(jitterLocal) || !isFinite(shimmerLocal)) {
            flags.add(flag("PY_METRIC_MISSING", "일부 음성 안정성 지표가 누락되어 점수 정확도가 낮을 수 있어요."));
        }

        return flags;
    }

    private FlagItem flag(String code, String msg) {
        return FlagItem.builder().code(code).message(msg).build();
    }

    private double nvl(Double v) {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return v;
    }

    private boolean isFinite(Double v) {
        return v != null && !Double.isNaN(v) && !Double.isInfinite(v);
    }
}