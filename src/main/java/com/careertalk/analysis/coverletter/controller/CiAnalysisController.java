package com.careertalk.analysis.coverletter.controller;

import com.careertalk.analysis.coverletter.dto.CIAnalyzeFormRequest;
import com.careertalk.analysis.coverletter.dto.CIAnalyzeResponse;
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
@CrossOrigin(origins = "http://localhost:5173")
public class CiAnalysisController {

    private final CiAnalysisService ciAnalysisService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyze(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("request") CIAnalyzeFormRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        try {
            CIAnalyzeResponse response = ciAnalysisService.analyzeAndSave(authorization, request, file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("분석 실패: " + e.getMessage());
        }
    }

    @PostMapping("/rewrite")
    public ResponseEntity<?> rewrite(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CiRewriteRequest request
    ) {
        try {
            CiRewriteResponse response = ciAnalysisService.rewriteFromText(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("개선본 생성 실패: " + e.getMessage());
        }
    }

    // 마이페이지에서 상세 결과를 보기 위한 GET API
    @GetMapping("/result/{analysisId}")
    public CiAnalysisResponse getAnalysisResult(@PathVariable Long analysisId) {
        return ciAnalysisService.getAnalysisResult(analysisId);
    }
}