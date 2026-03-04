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


    @GetMapping("/{analysisId}/result")
    public ResponseEntity<PortfolioAnalysisResponse> getPortfolioResult(
            @PathVariable("analysisId") Long analysisId) {



        PortfolioAnalysisResponse response = portfolioAnalysisService.getAnalysisResult(analysisId);

        return ResponseEntity.ok(response);
    }


}
