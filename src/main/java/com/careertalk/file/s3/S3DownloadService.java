package com.careertalk.file.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class S3DownloadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public Path downloadToTempFile(String s3Key, String originalFilename) throws Exception {
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "audio.webm"
                : originalFilename;

        String suffix = safeName.contains(".")
                ? safeName.substring(safeName.lastIndexOf('.'))
                : ".webm";

        Path tempFile = Files.createTempFile("careertalk-stt-", suffix);

        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(req);
             OutputStream out = Files.newOutputStream(tempFile)) {
            in.transferTo(out);
        }

        return tempFile;
    }
}