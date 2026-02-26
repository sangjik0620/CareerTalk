package com.careertalk.analysis.portfolio.controller;

import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.service.PortfolioAnalysisService;
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


    @PostMapping("/analyze")
    public PortfolioAnalysisResponse analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,

            @RequestParam(value = "detailedPosition", required = false) String detailedPosition
    ) {

        return portfolioAnalysisService.analyzeAndSave(file, jobCategory, detailedPosition);
    }

    @PostMapping("/{portfolioId}/reanalyze")
    public ResponseEntity<PortfolioAnalysisResponse> reanalyzePortfolio(
            @PathVariable("portfolioId") Long portfolioId) {

        log.info("재분석 요청 들어옴 - 포트폴리오 ID: {}", portfolioId);

        // 서비스의 reanalyze 메서드 호출!
        PortfolioAnalysisResponse response = portfolioAnalysisService.reanalyze(portfolioId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId) {


        // 방금 만든 Service 메서드 호출!
        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId);

        return ResponseEntity.ok(response);
    }


}
