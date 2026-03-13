package com.careertalk.file.dto;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private Long fileId;
    private String originalName;
    private String fileUrl;
    private String s3Key;
    private String message;
}