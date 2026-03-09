package com.careertalk.analysis.coverletter.dto;

import lombok.Data;

@Data
public class CiRewriteRequest {
    private Long analysisId;
    private String jobRole;
    private String jobDetail;
    private String title;
    private String content;
}