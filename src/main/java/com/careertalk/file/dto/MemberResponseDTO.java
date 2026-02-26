package com.careertalk.file.dto;

import com.careertalk.file.entity.Member; // 중요: Member 엔티티 위치에 맞게 확인
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponseDTO {
    private String loginId;
    private String email;
    private String nickname;
    private String name;

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     */
    public static MemberResponseDTO from(Member member) {
        return new MemberResponseDTO(
                member.getLoginId(),
                member.getEmail(),
                member.getNickname(),
                member.getName()
        );
    }
}