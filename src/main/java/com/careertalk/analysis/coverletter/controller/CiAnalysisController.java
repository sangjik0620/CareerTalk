package com.careertalk.analysis.coverletter.controller;

import com.careertalk.analysis.coverletter.dto.CiAnalysisResponse;
import com.careertalk.analysis.coverletter.dto.CiAnalyzeTextRequest;
import com.careertalk.analysis.coverletter.service.CiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ci")
@CrossOrigin(origins = "http://localhost:5173")
public class CiAnalysisController {

    private final CiAnalysisService ciAnalysisService;

    @PostMapping("/analyze/text")
    public CiAnalysisResponse analyzeText(
            @RequestBody CiAnalyzeTextRequest req) {

        return ciAnalysisService.analyzeFromText(req);
    }

    @PostMapping("/analyze/pdf")
    public CiAnalysisResponse analyzePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobRole") String jobRole,
            @RequestParam(value = "jobDetail", required = false) String jobDetail
    ) {
        return ciAnalysisService.analyzeFromPdf(file, jobRole, jobDetail);
    }
}