package com.careertalk.analysis.portfolio.controller;

import com.careertalk.analysis.portfolio.dto.PortfolioAnalysisResponse;
import com.careertalk.analysis.portfolio.service.PortfolioAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioAnalysisController {

    private final PortfolioAnalysisService portfolioAnalysisService;

    /**
     * 포트폴리오 정밀 분석 요청을 받는 API
     * 프론트엔드의 FormData(파일 + 직무 텍스트)를 처리합니다.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioAnalysisResponse> analyzePortfolio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("targetJob") String targetJob
    ) {
        // 1. 서비스에 파일과 직무 데이터를 넘겨서 분석 시작
        PortfolioAnalysisResponse response = portfolioAnalysisService.analyzeAndSave(file, targetJob);

        // 2. 분석이 끝난 예쁜 데이터 상자(DTO)를 프론트엔드로 반환 (HTTP 상태 코드 200)
        return ResponseEntity.ok(response);
    }
}
