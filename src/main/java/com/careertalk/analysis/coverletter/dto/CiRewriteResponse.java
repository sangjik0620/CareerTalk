package com.careertalk.analysis.coverletter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiRewriteResponse {
    private String rewrittenEssay;
    private List<String> changeSummary;
}