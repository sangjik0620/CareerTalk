package com.careertalk.auth.service;

import com.careertalk.auth.dto.LoginRequestDTO;
import com.careertalk.auth.dto.SignupRequestDTO;
import com.careertalk.auth.dto.SocialSignupRequestDTO;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
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

    /* 일반 로그인 */
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
        // 1. 이메일 중복 체크
//        if (memberRepository.findByLoginId(dto.getLoginId()).isPresent()) {
//            throw new RuntimeException("이미 가입된 계정입니다.");
//        }

        if (memberRepository.findByLoginId(dto.getLoginId()).isPresent()) {
            throw new RuntimeException("이미 가입된 계정입니다.");
        }

        // 2. 닉네임 중복 체크
        if (memberRepository.findByNickname(dto.getNickname()).isPresent()) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        // 3. 엔티티 변환
        Member user = Member.builder()
                .loginId(dto.getLoginId())
                .email(dto.getEmail())
                .name(dto.getName())
                .nickname(dto.getNickname())
                .phone(dto.getPhone())
                .birthDate(dto.getBirthDate())
                .targetJob(dto.getTargetJob())
                .password(passwordEncoder.encode("SOCIAL_AUTH_" + java.util.UUID.randomUUID()))
                .status("ACTIVE")
                .build();

        return memberRepository.save(user);
    }

    @Transactional
    public void deleteMember(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));
        memberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public boolean checkLoginIdDuplicate(String loginId) {
        return memberRepository.findByLoginId(loginId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean checkNicknameDuplicate(String nickname) {
        return memberRepository.findByNickname(nickname).isPresent();
    }

    // MemberService.java 내부
    @Transactional(readOnly = true)
    public Member findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId).orElse(null);
    }

}