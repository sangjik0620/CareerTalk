package com.careertalk.interview.scoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceFeedbackResult {
    private String summary;          // 한 줄 요약
    private List<String> strengths;  // 잘한 점
    private List<String> improvements; // 개선점
    private List<String> actionTips; // 바로 적용 팁
    private String version;          // "VF-1.0"
}