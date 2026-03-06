package com.careertalk.auth.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class SignupRequestDTO {

    private String loginId;
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String phone;
    private LocalDate birthDate;
    private String targetJob;

}