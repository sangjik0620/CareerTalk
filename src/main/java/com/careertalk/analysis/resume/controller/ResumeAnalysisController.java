package com.careertalk.analysis.resume.controller;

import com.careertalk.analysis.resume.dto.ResumeAnalysisResponse;
import com.careertalk.analysis.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/resumes")
public class ResumeAnalysisController {

    private final ResumeService resumeService;

    /**
     * 이력서 업로드 + AI 분석
     * POST /api/resumes/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<ResumeAnalysisResponse> analyze(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobCategory") String jobCategory,
            @RequestParam("detailedPosition") String detailedPosition
    ) {
        Long currentUserId = 1L; // TODO: 인증 붙이면 SecurityContext에서 추출
        ResumeAnalysisResponse response = resumeService.analyzeAndSave(file, jobCategory, detailedPosition, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * resumes(resumeId) -> files(fileId) -> S3 -> DOCX 파싱 텍스트 반환
     * GET /api/resumes/{resumeId}/parsed-text
     */
    @GetMapping("/{resumeId}/parsed-text")
    public ResponseEntity<Map<String, Object>> parsedTextByResumeId(@PathVariable Long resumeId) {
        Long currentUserId = 1L;
        return ResponseEntity.ok(resumeService.parseResumeTextByResumeId(resumeId, currentUserId));
    }

    /**
     * files(fileId) -> S3 -> DOCX 파싱 텍스트 반환
     * GET /api/resumes/files/{fileId}/parsed-text
     */
    @GetMapping("/files/{fileId}/parsed-text")
    public ResponseEntity<Map<String, Object>> parsedTextByFileId(@PathVariable Long fileId) {
        Long currentUserId = 1L;
        return ResponseEntity.ok(resumeService.parseResumeTextByFileId(fileId, currentUserId));
    }
}