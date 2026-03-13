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
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> actionTips;
    private String version;
}