package com.careertalk.analysis.resume.controller;

import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse;
import com.careertalk.analysis.resume.service.ResumeService;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/resumes")
public class ResumeAnalysisController {

    private final ResumeService resumeService;
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;

    // 헬퍼 메서드 추가 (과거 jwt필터 없을시 사용)
//    private Long extractUserNum(String authHeader) {
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            throw new RuntimeException("인증 정보가 없습니다.");
//        }
//        String token = authHeader.substring(7);
//        if (!jwtUtil.validateToken(token)) {
//            throw new RuntimeException("유효하지 않은 토큰입니다.");
//        }
//        String loginId = jwtUtil.getLoginId(token);
//        return memberRepository.findByLoginId(loginId)
//                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."))
//                .getUserNum();
//    }
    // 헬퍼 메서드 추가
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // Filter에서 Principal을 loginId(String)로 저장했으므로 String으로 꺼냄
        String loginId = (String) auth.getPrincipal();

        // DB에서 userNum 조회
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다."))
                .getUserNum();
    }

    /**
     * 이력서 업로드 + AI 분석
     * POST /api/resumes/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<ResumeAnalysisResponse> analyze(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam("detailedPosition") String detailedPosition
//            @RequestHeader("Authorization") String authHeader
    ) {
        Long currentUserId = getCurrentUserId();  // 1L 대체
        ResumeAnalysisResponse response = resumeService.analyzeAndSave(file, jobCategory, detailedPosition, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ 추가: 분석 결과 조회
     * GET /api/resumes/{analysisId}/result
     */
    @GetMapping("/{analysisId}/result")
    public ResponseEntity<ResumeAnalysisResponse> getResumeResult(
            @PathVariable("analysisId") Long analysisId
    ) {
        Long currentUserNum = getCurrentUserId();
        ResumeAnalysisResponse response = resumeService.getAnalysisResult(analysisId, currentUserNum);
        return ResponseEntity.ok(response);
    }

}