package com.careertalk.interview.scoring;

import com.careertalk.interview.scoring.dto.VoiceFeedbackResult;

import java.util.ArrayList;
import java.util.List;

public class VoiceFeedbackGenerator {

    private static final String VERSION = "VF-1.0";

    public VoiceFeedbackResult generate(
            Integer overallScore,
            String overallGrade,
            Double overallReliability,
            Integer tremorRiskScore,
            Integer confidenceScore,
            Integer fluencyScore
    ) {
        int o = overallScore == null ? 50 : overallScore;
        double r = overallReliability == null ? 0.7 : overallReliability;

        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        List<String> tips = new ArrayList<>();

        String summary = buildSummary(o, overallGrade, r);

        if ("EXCELLENT".equals(overallGrade)) {
            strengths.add("전반적으로 안정적이고 자신감 있는 발화 흐름이에요.");
            strengths.add("면접 상황에서 신뢰감 있는 인상을 줄 가능성이 높아요.");
            tips.add("지금의 톤/속도를 유지하되, 핵심 문장에만 약간 더 힘을 실어보세요.");
        } else if ("GOOD".equals(overallGrade)) {
            strengths.add("전반적인 발화 흐름이 괜찮고 전달력이 있어요.");
            improvements.add("일부 구간에서 호흡/멈춤을 더 정리하면 더 안정적으로 들려요.");
            tips.add("문장 끝(종결 어미)을 또렷하게 마무리하는 습관을 가져보세요.");
        } else if ("FAIR".equals(overallGrade)) {
            strengths.add("내용 전달은 가능하지만, 안정감과 흐름이 조금 흔들릴 수 있어요.");
            improvements.add("멈춤(침묵)과 발화 안정성을 개선하면 점수가 빠르게 올라가요.");
            tips.add("질문을 들은 뒤 1초 숨-정리 후, 2~3문장으로 끊어 말해보세요.");
        } else {
            improvements.add("긴장/불안정 요인이 커서 자신감이 낮게 들릴 수 있어요.");
            improvements.add("발화 속도/멈춤/발성 안정성을 함께 개선하는 게 좋아요.");
            tips.add("‘짧게-또렷하게’(한 문장 10~12단어)로 말하는 훈련부터 시작해보세요.");
        }

        if (r < 0.55) {
            improvements.add("분석 구간이 짧거나(또는 침묵이 많아) 신뢰도가 낮을 수 있어요.");
            tips.add("답변을 최소 6~10초 이상 말한 구간으로 다시 분석해보면 정확도가 좋아져요.");
        }

        if (tremorRiskScore != null && tremorRiskScore >= 75) {
            improvements.add("발성 흔들림(떨림)이 감지되어 불안하게 들릴 수 있어요.");
            tips.add("호흡을 먼저 잡고(복식호흡), 첫 문장을 천천히 시작해보세요.");
        }
        if (confidenceScore != null && confidenceScore <= 55) {
            improvements.add("자신감 지표가 낮게 나왔어요(음량/멈춤/안정성 영향).");
            tips.add("“결론 먼저 → 근거 2개” 구조로 말하면 멈춤이 줄어들어요.");
        }
        if (fluencyScore != null && fluencyScore <= 55) {
            improvements.add("말의 흐름(유창성)이 끊기는 패턴이 있어요.");
            tips.add("‘한 호흡 한 문장’(짧게 끊기) + ‘접속사 줄이기’로 흐름이 좋아져요.");
        }

        strengths = uniqueTop(strengths, 3);
        improvements = uniqueTop(improvements, 4);
        tips = uniqueTop(tips, 4);

        return VoiceFeedbackResult.builder()
                .summary(summary)
                .strengths(strengths)
                .improvements(improvements)
                .actionTips(tips)
                .version(VERSION)
                .build();
    }

    private String buildSummary(int score, String grade, double reliability) {
        String g = grade == null ? "FAIR" : grade;
        String rel = reliability >= 0.75 ? "높음" : (reliability >= 0.55 ? "보통" : "낮음");
        return "종합 음성 점수 " + score + "점(" + g + "), 분석 신뢰도 " + rel + "입니다.";
    }

    private List<String> uniqueTop(List<String> list, int max) {
        return list.stream().distinct().limit(max).toList();
    }
}