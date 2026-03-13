package com.careertalk.auth.service;

import com.careertalk.analysis.common.repository.AnalysisRepository;
import com.careertalk.analysis.coverletter.repository.CIEssayRepository;
import com.careertalk.analysis.portfolio.repository.PortfolioRepository;
import com.careertalk.analysis.resume.repository.ResumeRepository;
import com.careertalk.auth.dto.LoginRequestDTO;
import com.careertalk.auth.dto.SignupRequestDTO;
import com.careertalk.auth.dto.SocialSignupRequestDTO;
import com.careertalk.auth.dto.UpdateRequestDTO;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import com.careertalk.file.repository.FileRepository;
import com.careertalk.interview.repository.InterviewSessionRepository;
import com.careertalk.payment.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import com.careertalk.auth.jwt.JwtUtil;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final CIEssayRepository ciEssayRepository;
    private final PortfolioRepository portfolioRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final QuotaService quotaService;

    private final FileRepository fileRepository;

    /* 일반 회원가입 */
    public String signup(SignupRequestDTO signupRequestDTO) {
        // 1. 이메일 중복 체크
        if (isDuplicateNormalEmail(signupRequestDTO.getEmail())) {
            throw new RuntimeException("이미 동일한 이메일로 가입된 계정이 존재합니다.");
        }

        // 1-1. 아이디 중복 체크
        if (memberRepository.findByLoginId(signupRequestDTO.getLoginId()).isPresent()) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }

        // 1-2. 닉네임 중복 체크
        if (memberRepository.findByNickname(signupRequestDTO.getNickname()).isPresent()) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        // 2. 비밀번호 암호화 및 엔티티 변환
        Member user = Member.builder()
                .loginId(signupRequestDTO.getLoginId())
                .email(signupRequestDTO.getEmail())
                .password(passwordEncoder.encode(signupRequestDTO.getPassword()))
                .name(signupRequestDTO.getName())
                .nickname(signupRequestDTO.getNickname())
                .phone(signupRequestDTO.getPhone())
                .birthDate(signupRequestDTO.getBirthDate())
                .targetJob(signupRequestDTO.getTargetJob())
                .status("ACTIVE")
                .build();

        // 3. DB 저장
        Member savedUser = memberRepository.save(user);

        // 4. 신규 가입자 기본 이용권 생성
        quotaService.createInitialQuota(savedUser.getUserNum());

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

        // 3. 로그인 성공 시 JWT 반환
        return jwtUtil.createToken(user.getLoginId(), "ROLE_USER");
    }

    /* 소셜 회원가입 완료 */
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
                .password(passwordEncoder.encode("SOCIAL_AUTH_" + UUID.randomUUID()))
                .status("ACTIVE")
                .build();

        // 저장
        Member savedUser = memberRepository.save(user);

        // 신규 소셜 가입자 기본 이용권 생성
        quotaService.createInitialQuota(savedUser.getUserNum());

        return savedUser;
    }

    public boolean isDuplicateNormalEmail(String email) {
        List<Member> members = memberRepository.findAllByEmail(email);

        if (members.isEmpty()) return false;

        return members.stream().anyMatch(member -> {
            String loginId = member.getLoginId();
            return !(loginId.startsWith("google_")
                    || loginId.startsWith("kakao_")
                    || loginId.startsWith("naver_"));
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
        // 1. 회원 정보 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        Long userNum = member.getUserNum();

        // 분석 통합 기록 삭제
        analysisRepository.deleteByUserNum(userNum);

        // 이력서, 자기소개서, 포트폴리오 개별 데이터 삭제
        resumeRepository.deleteByUserNum(userNum);
        ciEssayRepository.deleteByUserNum(userNum);
        portfolioRepository.deleteByUserNum(userNum);

        // 연관된 모든 데이터 삭제
        fileRepository.deleteByUserNum(userNum);

        // 면접 세션 기록 삭제
        interviewSessionRepository.deleteByUserNum(userNum);

        // 3. 최종적으로 회원 계정 삭제
        memberRepository.delete(member);
    }
}