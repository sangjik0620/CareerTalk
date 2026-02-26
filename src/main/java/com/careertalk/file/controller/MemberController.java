package com.careertalk.file.controller;

import com.careertalk.file.dto.LoginRequestDTO;
import com.careertalk.file.dto.MemberResponseDTO; // 1. DTO 임포트 추가
import com.careertalk.file.dto.SignupRequestDTO;
import com.careertalk.file.dto.SocialSignupRequestDTO;
import com.careertalk.file.entity.Member;
import com.careertalk.file.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class MemberController {

    private final MemberService memberService;

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

}