package com.careertalk.auth.dto;

import com.careertalk.auth.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder; // 빌더 패턴 추가 (선택사항)
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberResponseDTO {
    private String loginId;
    private String email;
    private String nickname;
    private String name;
    private String phone;
    private String targetJob;

    public static MemberResponseDTO from(Member member) {
        return MemberResponseDTO.builder()
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .name(member.getName())
                .phone(member.getPhone())
                .targetJob(member.getTargetJob())
                .build();
    }
}