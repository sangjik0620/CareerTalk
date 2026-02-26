package com.careertalk.file.service;

import com.careertalk.file.dto.LoginRequestDTO;
import com.careertalk.file.dto.SignupRequestDTO;
import com.careertalk.file.dto.SocialSignupRequestDTO;
import com.careertalk.file.entity.Member;
import com.careertalk.file.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /* 일반 회원가입 */
    public String signup(SignupRequestDTO signupRequestDTO) {
        // 1. 이메일 중복 체크
        if (memberRepository.findByEmail(signupRequestDTO.getEmail()).isPresent()) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        // 2. 비밀번호 암호화 및 엔티티 변환
        Member user = Member.builder()
                .loginId(signupRequestDTO.getLoginId())
                .email(signupRequestDTO.getEmail())
                .password(passwordEncoder.encode(signupRequestDTO.getPassword())) // 암호화!
                .name(signupRequestDTO.getName())
                .nickname(signupRequestDTO.getNickname())
                .phone(signupRequestDTO.getPhone())
                .birthDate(signupRequestDTO.getBirthDate())
                .targetJob(signupRequestDTO.getTargetJob())
                .status("ACTIVE")
                .build();

        // 3. DB 저장
        memberRepository.save(user);
        return "회원가입 성공";
    }

    /**
     * 일반 로그인
     */
    public Member login(LoginRequestDTO loginRequestDTO) {
        // 1. 이메일로 사용자 조회
        Member user = memberRepository.findByLoginId(loginRequestDTO.getLoginId())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 계정입니다."));

        // 2. 비밀번호 일치 확인
        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 로그인 성공 시 유저 객체 반환 (실무에서는 여기서 JWT 토큰을 발급하기도 합니다)
        return user;
    }

    public Member socialSignupComplete(SocialSignupRequestDTO dto) {
        // 1. 이미 가입된 이메일인지 한 번 더 확인
        if (memberRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("이미 가입된 계정입니다.");
        }

        // 2. 엔티티 변환
        Member user = Member.builder()
                .loginId(dto.getLoginId())
                .email(dto.getEmail())
                .name(dto.getName())
                .nickname(dto.getNickname())
                .phone(dto.getPhone())
                .birthDate(dto.getBirthDate())
                .targetJob(dto.getTargetJob())
                // ⭐ 중요: 소셜 로그인은 비밀번호가 없지만 DB 제약조건(NOT NULL) 때문에 임의값 입력
                .password(passwordEncoder.encode("SOCIAL_AUTH_" + java.util.UUID.randomUUID()))
                .status("ACTIVE")
                .build();

        // 3. DB 저장 및 저장된 객체 반환
        return memberRepository.save(user);
    }

}