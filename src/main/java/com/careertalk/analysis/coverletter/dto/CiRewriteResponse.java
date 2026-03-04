package com.careertalk.analysis.coverletter.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CiRewriteResponse {
    private String rewrittenEssay;     // 개선본 본문
    private List<String> changeSummary; // 변경 요약(3~6개 추천)
}