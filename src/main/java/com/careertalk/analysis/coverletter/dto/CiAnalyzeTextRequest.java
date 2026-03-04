package com.careertalk.analysis.coverletter.dto;

import lombok.Data;

@Data
public class CiAnalyzeTextRequest {
    private String jobRole;
    private String jobDetail;

    private String title;
    private String content;
}
