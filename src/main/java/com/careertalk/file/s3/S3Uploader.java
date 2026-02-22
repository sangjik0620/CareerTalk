package com.careertalk.file.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.base-prefix:careertalk}")
    private String basePrefix;

    @Value("${aws.s3.region}")
    private String region;

    public UploadResult upload(MultipartFile file, String keyPrefix) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("\\s+", "_");
        String key = basePrefix + "/" + keyPrefix + "/" + UUID.randomUUID() + "_" + safeName;

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        return new UploadResult(bucket, key, url, file.getContentType(), file.getSize());
    }

    public record UploadResult(String bucket, String s3Key, String url, String contentType, long size) {}
}