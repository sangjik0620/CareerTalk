package com.careertalk.analysis.portfolio.controller;

import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.service.PortfolioAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioAnalysisController {

    private final PortfolioAnalysisService portfolioAnalysisService;

    @PostMapping("/analyze")
    public PortfolioAnalysisResponse analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam(value = "detailedPosition", required = false) String detailedPosition,
            //  인증된 사용자 정보를 가져옵니다.
            @AuthenticationPrincipal Long userNum
    ) {
        log.info("포트폴리오 분석 요청 - User: {}, 직무: {}", userNum, jobCategory);

        // 서비스 메서드에 userNum을 함께 넘겨줍니다.
        return portfolioAnalysisService.analyzeAndSave(file, jobCategory, detailedPosition, userNum);
    }

    /**
     * 분석 결과 조회 (본인 확인 로직 포함)
     */
    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId,
            @AuthenticationPrincipal Long userNum
    ) {
        log.info("분석 결과 조회 요청 - AnalysisId: {}, User: {}", analysisId, userNum);

        // 서비스에서 본인의 결과인지 확인하는 로직이 추가되어야 합니다.
        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId, userNum);

        return ResponseEntity.ok(response);
    }
}
