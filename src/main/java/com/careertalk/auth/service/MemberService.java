package com.careertalk.auth.service;

import com.careertalk.auth.dto.LoginRequestDTO;
import com.careertalk.auth.dto.SignupRequestDTO;
import com.careertalk.auth.dto.SocialSignupRequestDTO;
import com.careertalk.auth.dto.UpdateRequestDTO;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.careertalk.auth.jwt.JwtUtil;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /* 일반 회원가입 */
    public String signup(SignupRequestDTO signupRequestDTO) {
        // 1. 이메일 중복 체크
        if (isDuplicateNormalEmail(signupRequestDTO.getEmail())) {
            throw new RuntimeException("이미 동일한 이메일로 가입된 계정이 존재합니다.");
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
    public String login(LoginRequestDTO loginRequestDTO) {
        // 1. 아이디로 사용자 조회
        Member user = memberRepository.findByLoginId(loginRequestDTO.getLoginId())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 계정입니다."));

        // 2. 비밀번호 일치 확인
        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 로그인 성공 시 유저 객체 반환
//        return user;
        return jwtUtil.createToken(user.getLoginId(), "ROLE_USER");
    }

    public Member socialSignupComplete(SocialSignupRequestDTO dto) {

        if (memberRepository.findByLoginId(dto.getLoginId()).isPresent()) {
            throw new RuntimeException("이미 가입된 계정입니다.");
        }

        // 닉네임 중복 체크
        if (memberRepository.findByNickname(dto.getNickname()).isPresent()) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        // 엔티티 변환
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

    public boolean isDuplicateNormalEmail(String email) {
        // 1. findByEmail 대신 findAllByEmail(리스트 반환)을 사용하여 에러 원천 차단
        List<Member> members = memberRepository.findAllByEmail(email);

        // 2. 검색된 결과가 없으면 당연히 중복 아님
        if (members.isEmpty()) return false;

        // 3. 검색된 모든 계정 중 하나라도 '일반 계정'인지 확인
        return members.stream().anyMatch(member -> {
            String loginId = member.getLoginId();
            // 소셜 계정(google_, kakao_, naver_)이 아닌 경우만 true
            return !(loginId.startsWith("google_") ||
                    loginId.startsWith("kakao_") ||
                    loginId.startsWith("naver_"));
        });
    }

    @Transactional
    public void deleteMember(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));
        memberRepository.delete(member);
    }

    @Transactional
    public void updateMember(UpdateRequestDTO dto) {
        Member member = memberRepository.findByLoginId(dto.getLoginId())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!member.getNickname().equals(dto.getNickname())) {
            if (checkNicknameDuplicate(dto.getNickname())) {
                throw new RuntimeException("이미 사용 중인 닉네임입니다.");
            }
        }
        member.updateInfo(dto.getName(), dto.getNickname(), dto.getEmail());
    }

    @Transactional(readOnly = true)
    public boolean checkLoginIdDuplicate(String loginId) {
        return memberRepository.findByLoginId(loginId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean checkNicknameDuplicate(String nickname) {
        return memberRepository.findByNickname(nickname).isPresent();
    }

    @Transactional(readOnly = true)
    public Member findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId).orElse(null);
    }

    @Transactional
    public void deleteMemberByLoginId(String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 없음"));
        memberRepository.delete(member);
    }

}