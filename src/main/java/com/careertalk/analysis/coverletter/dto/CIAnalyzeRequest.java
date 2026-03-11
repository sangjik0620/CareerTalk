package com.careertalk.analysis.coverletter.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CIAnalyzeRequest {
    private String title;
    private String content;
    private String targetJob;
}