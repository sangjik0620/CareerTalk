package com.careertalk.auth.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequestDTO {
    private String loginId;
    private String name;
    private String nickname;
    private String email;
}