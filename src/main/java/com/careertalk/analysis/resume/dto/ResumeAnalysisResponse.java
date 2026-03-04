package com.careertalk.analysis.resume.dto;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ResumeAnalysisResponse {
    private Long resumeId;
    private Long userId;
    private Long fileId;

    private String resumeTitle;
    private String status;
    private String createAt;
    private String updateAt;
}
