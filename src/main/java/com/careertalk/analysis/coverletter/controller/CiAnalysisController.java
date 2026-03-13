package com.careertalk.analysis.coverletter.controller;

import com.careertalk.analysis.coverletter.dto.CIAnalyzeFormRequest;
import com.careertalk.analysis.coverletter.dto.CIAnalyzeResponse;
import com.careertalk.analysis.coverletter.dto.CiAnalysisResponse;
import com.careertalk.analysis.coverletter.dto.CiRewriteRequest;
import com.careertalk.analysis.coverletter.dto.CiRewriteResponse;
import com.careertalk.analysis.coverletter.service.CiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ci")
@RequiredArgsConstructor
public class CiAnalysisController {

    private final CiAnalysisService ciAnalysisService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CIAnalyzeResponse> analyze(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("request") CIAnalyzeFormRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        CIAnalyzeResponse response = ciAnalysisService.analyzeAndSave(authorization, request, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rewrite")
    public ResponseEntity<CiRewriteResponse> rewrite(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CiRewriteRequest request
    ) {
        CiRewriteResponse response = ciAnalysisService.rewriteFromText(authorization, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{analysisId}")
    public ResponseEntity<CiAnalysisResponse> getAnalysisResult(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long analysisId
    ) {
        CiAnalysisResponse response = ciAnalysisService.getAnalysisResult(authorization, analysisId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{analysisId}")
    public ResponseEntity<String> deleteAnalysis(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long analysisId
    ) {
        ciAnalysisService.deleteCoverLetterAnalysis(authorization, analysisId);
        return ResponseEntity.ok("자기소개서 분석 기록이 삭제되었습니다.");
    }
}