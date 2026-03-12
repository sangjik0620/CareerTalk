package com.careertalk.interview.util;

import java.util.List;
import java.util.Map;

public final class InterviewJobCompetencyTemplate {

    private static final Map<String, List<String>> TECH_LABELS = Map.ofEntries(
            Map.entry("기획∙전략", List.of("문제정의", "전략수립", "데이터분석", "의사결정")),
            Map.entry("마케팅∙홍보∙조사", List.of("시장분석", "마케팅전략", "콘텐츠기획", "데이터분석")),
            Map.entry("회계∙세무∙재무", List.of("재무분석", "회계지식", "리스크관리", "데이터해석")),
            Map.entry("인사∙노무∙HRD", List.of("채용기획", "조직관리", "인사제도이해", "노무이해")),
            Map.entry("총무∙법무∙사무", List.of("문서관리", "업무조정", "법규이해", "행정처리")),
            Map.entry("IT개발∙데이터", List.of("프로그래밍", "시스템설계", "데이터처리", "문제해결")),
            Map.entry("디자인", List.of("시각표현", "UIUX설계", "콘텐츠기획", "툴활용")),
            Map.entry("영업∙판매∙무역", List.of("고객분석", "협상력", "매출전략", "시장분석")),
            Map.entry("고객상담∙TM", List.of("고객응대", "문제해결", "설득력", "상담운영")),
            Map.entry("구매∙자재∙물류", List.of("재고관리", "공급망이해", "원가관리", "데이터분석")),
            Map.entry("상품기획∙MD", List.of("상품기획", "트렌드분석", "매출분석", "시장조사")),
            Map.entry("운전∙운송∙배송", List.of("안전관리", "운행관리", "시간관리", "고객응대")),
            Map.entry("서비스", List.of("고객경험관리", "문제해결", "커뮤니케이션", "서비스운영")),
            Map.entry("생산", List.of("공정관리", "품질관리", "설비이해", "안전관리")),
            Map.entry("건설∙건축", List.of("설계이해", "공정관리", "안전관리", "도면해석")),
            Map.entry("의료", List.of("환자관리", "의료지식", "의사소통", "윤리의식")),
            Map.entry("연구∙R&D", List.of("연구설계", "데이터분석", "문제탐구", "논리적사고")),
            Map.entry("교육", List.of("교육설계", "지식전달", "학습관리", "커뮤니케이션")),
            Map.entry("미디어∙문화∙스포츠", List.of("콘텐츠기획", "창의성", "트렌드분석", "커뮤니케이션")),
            Map.entry("금융∙보험", List.of("금융분석", "리스크관리", "상품이해", "고객상담")),
            Map.entry("공공∙복지", List.of("정책이해", "행정관리", "상담능력", "공공서비스"))
    );

    private static final List<String> DEFAULT_TECH_LABELS =
            List.of("직무이해", "문제해결", "분석력", "실행력");

    private static final List<String> SOFT_LABELS =
            List.of("커뮤니케이션", "팀워크", "리더십", "발표력");

    private InterviewJobCompetencyTemplate() {
    }

    public static List<String> technicalLabels(String jobCategory) {
        if (jobCategory == null || jobCategory.isBlank()) {
            return DEFAULT_TECH_LABELS;
        }
        return TECH_LABELS.getOrDefault(jobCategory.trim(), DEFAULT_TECH_LABELS);
    }

    public static List<String> softLabels() {
        return SOFT_LABELS;
    }
}