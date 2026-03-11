package com.careertalk.analysis.portfolio.controller;


import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.service.PortfolioAnalysisService;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
import com.careertalk.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;



@Slf4j
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioAnalysisController {

    private final PortfolioAnalysisService portfolioAnalysisService;
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    @PostMapping("/analyze")
    public PortfolioAnalysisResponse analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam(value = "detailedPosition", required = false) String detailedPosition,
            @RequestHeader("Authorization") String authHeader
    ) {
        // 1. 토큰 추출
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        String token = authHeader.substring(7);

        // 2. JwtUtil을 사용하여 직접 아이디 추출

        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("유효하지 않은 토큰입니다.");
        }
        String loginId = jwtUtil.getLoginId(token);

        // 3. 추출한 아이디로 서비스 호출
        return portfolioAnalysisService.analyzeAndSave(file, jobCategory, detailedPosition, loginId);
    }

    /* 분석 결과 조회 (본인 확인 로직 포함) */
    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId,
            @RequestHeader("Authorization") String authHeader //
    ) {
        // 1. 토큰에서 loginId 추출
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("인증 헤더가 누락되었습니다.");
        }
        String token = authHeader.substring(7);
        String loginId = jwtUtil.getLoginId(token);

        // 2. DB에서 실제 Member의 userNum 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        Long userNum = member.getUserNum();

        log.info("분석 결과 조회 요청 - AnalysisId: {}, UserNum: {}", analysisId, userNum);

        // 3. 서비스 호출
        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId, userNum);

        return ResponseEntity.ok(response);
    }
}
