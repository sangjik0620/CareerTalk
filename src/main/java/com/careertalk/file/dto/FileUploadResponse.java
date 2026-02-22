package com.careertalk.file.dto;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private Long fileId;
    private String originalName;
    private String fileUrl;     // S3 URL or presigned URL
    private String s3Key;       // 저장 키
    private String message;     // optional
}