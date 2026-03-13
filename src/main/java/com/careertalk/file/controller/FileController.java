package com.careertalk.file.controller;

import com.careertalk.file.dto.FileUploadResponse;
import com.careertalk.file.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final S3Service s3Service;

    @PostMapping
    public FileUploadResponse upload(@RequestParam MultipartFile file) throws IOException {

        long userId = 1L;
        String key = s3Service.uploadFile(file, userId);

        return FileUploadResponse.builder()
                .s3Key(key)
                .originalName(file.getOriginalFilename())
                .message("업로드 성공")
                .build();
    }
}