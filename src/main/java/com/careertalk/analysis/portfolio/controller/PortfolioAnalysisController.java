package com.careertalk.analysis.portfolio.controller;

import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.service.PortfolioAnalysisService;
import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioAnalysisController {

    private final PortfolioAnalysisService portfolioAnalysisService;
    private final MemberRepository memberRepository;

    /* 1. 포트폴리오 분석 요청 (이용권 차감 포함) */
    @PostMapping("/analyze")
    public PortfolioAnalysisResponse analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam(value = "detailedPosition", required = false) String detailedPosition
    ) {
        // 1. 필터(JwtAuthenticationFilter)를 통과하며 SecurityContext에 저장된 유저 아이디 추출
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        log.info("포트폴리오 분석 요청 - User: {}, 직군: {}", loginId, jobCategory);

        // 2. 서비스 단으로 바로 넘기기 (이용권 차감 -> 분석 -> 결과 반환)
        return portfolioAnalysisService.analyzeAndSave(file, jobCategory, detailedPosition, loginId);
    }

    /* 2. 분석 결과 단건 조회 */
    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId
    ) {
        // 1. SecurityContext에서 로그인한 유저 아이디 추출
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. DB에서 실제 Member의 userNum 조회
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        Long userNum = member.getUserNum();

        log.info("포트폴리오 분석 결과 조회 요청 - AnalysisId: {}, UserNum: {}", analysisId, userNum);

        // 3. 서비스 호출
        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId, userNum);

        return ResponseEntity.ok(response);
    }
}