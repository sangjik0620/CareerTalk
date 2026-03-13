package com.careertalk.interview.scoring.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FlagItem {
    private String code;
    private String message;
}