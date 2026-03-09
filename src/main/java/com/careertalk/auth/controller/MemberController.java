package com.careertalk.auth.controller;

import com.careertalk.auth.dto.*;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.repository.MemberRepository;
import com.careertalk.auth.service.EmailService;
import com.careertalk.auth.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class MemberController {

    private final MemberService memberService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final Map<String, String> emailAuthMap = new ConcurrentHashMap<>();

    /* 일반 회원가입 처리 */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequestDTO signupRequestDTO) {
        try {
            String result = memberService.signup(signupRequestDTO);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /* 일반 로그인 처리 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequestDTO) {
        try {
            String token = memberService.login(loginRequestDTO);
            Member member = memberService.findByLoginId(loginRequestDTO.getLoginId());
            if (member == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("유저 정보를 찾을 수 없습니다.");
            }
            MemberResponseDTO userDto = MemberResponseDTO.from(member);
            return ResponseEntity.ok(Map.of(
                    "accessToken", token,
                    "user", userDto
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 에러: " + e.getMessage());
        }
    }

    @PostMapping("/social-signup-complete")
    public ResponseEntity<?> socialSignupComplete(@RequestBody SocialSignupRequestDTO requestDTO) {
        try {
            Member savedMember = memberService.socialSignupComplete(requestDTO);

            String token = jwtUtil.createToken(savedMember.getLoginId(), "ROLE_USER");

            return ResponseEntity.ok(Map.of(
                    "accessToken", token,
                    "user", MemberResponseDTO.from(savedMember)
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/me")
    public ResponseEntity<?> deleteMember(@RequestHeader("Authorization") String token) {
        try {
            String jwtToken = token.substring(7);
            String loginId = jwtUtil.getLoginId(jwtToken);

            memberService.deleteMemberByLoginId(loginId);

            return ResponseEntity.ok("탈퇴 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("탈퇴 실패: " + e.getMessage());
        }
    }

    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId) {
        boolean isDuplicate = memberService.checkLoginIdDuplicate(loginId);
        return ResponseEntity.ok(isDuplicate);
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<Boolean> checkNickname(@RequestParam("nickname") String nickname) {
        boolean isDuplicate = memberService.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok(isDuplicate);
    }

    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(@RequestParam("email") String email) {
        try {
            if (memberService.isDuplicateNormalEmail(email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("DUPLICATE_NORMAL_EMAIL");
            }

            String code = emailService.createCode();
            emailAuthMap.put(email, code);
            emailService.sendEmail(email, code);

            return ResponseEntity.ok("인증 메일이 발송되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("메일 발송 실패: " + e.getMessage());
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Boolean> verifyEmail(
            @RequestParam("email") String email,
            @RequestParam("code") String code) {

        String originCode = emailAuthMap.get(email);

        if (originCode == null) {
            return ResponseEntity.ok(false);
        }

        boolean isMatch = code.equals(originCode);

        if (isMatch) {
            emailAuthMap.remove(email);
        }

        return ResponseEntity.ok(isMatch);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증이 필요합니다.");
        }

        try {
            String jwtToken = token.substring(7);
            String loginId = jwtUtil.getLoginId(jwtToken);
            Member member = memberService.findByLoginId(loginId);

            if (member == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자 없음");
            }

            return ResponseEntity.ok(MemberResponseDTO.from(member));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰");
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateInfo(@RequestBody UpdateRequestDTO dto) {
        try {
            memberService.updateMember(dto);
            return ResponseEntity.ok("정보가 수정되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("수정 실패: " + e.getMessage());
        }
    }

}