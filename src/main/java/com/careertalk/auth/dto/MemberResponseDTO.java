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
    private String phone;      // ⭐ 추가: 전화번호
    private String targetJob;  // ⭐ 추가: 목표 직무

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     */
    public static MemberResponseDTO from(Member member) {
        return MemberResponseDTO.builder()
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .name(member.getName())
                .phone(member.getPhone())      // ⭐ 추가
                .targetJob(member.getTargetJob()) // ⭐ 추가
                .build();
    }
}