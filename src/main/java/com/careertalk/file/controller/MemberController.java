package com.careertalk.file.controller;

import com.careertalk.file.dto.LoginRequestDTO;
import com.careertalk.file.dto.MemberResponseDTO; // 1. DTO 임포트 추가
import com.careertalk.file.dto.SignupRequestDTO;
import com.careertalk.file.dto.SocialSignupRequestDTO;
import com.careertalk.file.entity.Member;
import com.careertalk.file.service.EmailService;
import com.careertalk.file.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class MemberController {

    private final MemberService memberService;
    private final EmailService emailService;

    private final Map<String, String> emailAuthMap = new ConcurrentHashMap<>();

    /**
     * 일반 회원가입 처리
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequestDTO signupRequestDTO) {
        try {
            String result = memberService.signup(signupRequestDTO);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 일반 로그인 처리
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequestDTO) {
        try {
            Member user = memberService.login(loginRequestDTO);

            // 2. HashMap 대신 MemberResponseDTO의 정적 팩토리 메서드 사용
            return ResponseEntity.ok(MemberResponseDTO.from(user));

        } catch (RuntimeException e) {
            // 401 Unauthorized 상태 코드를 명시적으로 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/social-signup-complete")
    public ResponseEntity<?> socialSignupComplete(@RequestBody SocialSignupRequestDTO requestDTO) {
        try {
            Member savedMember = memberService.socialSignupComplete(requestDTO);

            // 가입 완료 후 로그인된 상태로 정보를 반환 (기존 ResponseDTO 활용)
            return ResponseEntity.ok(MemberResponseDTO.from(savedMember));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{email}")
    public ResponseEntity<?> deleteMember(@PathVariable String email) {
        try {
            memberService.deleteMember(email);
            return ResponseEntity.ok("회원 탈퇴가 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // MemberController 클래스 안에 추가
    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId) {
        boolean isDuplicate = memberService.checkLoginIdDuplicate(loginId);
        return ResponseEntity.ok(isDuplicate); // 중복이면 true, 아니면 false 반환
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<Boolean> checkNickname(@RequestParam("nickname") String nickname) {
        boolean isDuplicate = memberService.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok(isDuplicate);
    }

    // MemberController 내부에 추가
    private String savedCode; // 임시 저장 (실무에선 Redis나 세션 활용 권장)

    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(@RequestParam("email") String email) {
        try {
            String code = emailService.createCode();
            // ⭐ 개선: 전체 공유 변수가 아닌, 해당 이메일에 매핑된 코드를 저장
            emailAuthMap.put(email, code);

            emailService.sendEmail(email, code);
            return ResponseEntity.ok("인증 메일이 발송되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("메일 발송 실패: " + e.getMessage());
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Boolean> verifyEmail(@RequestParam("email") String email, @RequestParam("code") String code) {
        // ⭐ 개선: 요청받은 이메일로 저장된 코드를 꺼내와서 비교
        String originCode = emailAuthMap.get(email);
        boolean isMatch = code.equals(originCode);

        if (isMatch) {
            emailAuthMap.remove(email); // 인증 성공 시 코드 삭제 (보안상 권장)
        }

        return ResponseEntity.ok(isMatch);
    }

}