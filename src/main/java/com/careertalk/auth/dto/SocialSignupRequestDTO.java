package com.careertalk.auth.dto;

import lombok.*;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialSignupRequestDTO {
    private String loginId;     // 구글 고유 ID (google_xxxx)
    private String email;
    private String name;
    private String nickname;    // 사용자가 입력한 값
    private String phone;
    private LocalDate birthDate;
    private String targetJob;   // 사용자가 선택한 값
}