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

    /*  포트폴리오 분석 요청  */
    @PostMapping("/analyze")
    public PortfolioAnalysisResponse analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam(value = "detailedPosition", required = false) String detailedPosition
    ) {
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        log.info("포트폴리오 분석 요청 - User: {}, 직군: {}", loginId, jobCategory);

        return portfolioAnalysisService.analyzeAndSave(file, jobCategory, detailedPosition, loginId);
    }

    /*  분석 결과 단건 조회 */
    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId
    ) {
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        Long userNum = member.getUserNum();

        log.info("포트폴리오 분석 결과 조회 요청 - AnalysisId: {}, UserNum: {}", analysisId, userNum);

        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId, userNum);

        return ResponseEntity.ok(response);
    }

    /* 포트폴리오 분석 결과 삭제  */
    @DeleteMapping("/{analysisId}")
    public ResponseEntity<String> deletePortfolioAnalysis(
            @PathVariable("analysisId") Long analysisId
    ) {
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        log.info("포트폴리오 분석 삭제 요청 - AnalysisId: {}, User: {}", analysisId, loginId);

        portfolioAnalysisService.deletePortfolioAnalysis(analysisId, loginId);

        return ResponseEntity.ok("포트폴리오 분석 기록과 파일이 성공적으로 삭제되었습니다.");
    }
}