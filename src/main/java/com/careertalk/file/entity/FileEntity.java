package com.careertalk.file.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "files",
        uniqueConstraints = @UniqueConstraint(name = "uk_files_bucket_keyhash", columnNames = {"s3_bucket", "s3_key_hash"}))
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    // RESUME / ESSAY / PORTFOLIO / AUDIO
    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 700)
    private String s3Key;

    @Lob
    @Column(name = "s3_key_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] s3KeyHash;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "checksum", length = 128)
    private String checksum;

    // ACTIVE / DELETED
    @Column(name = "status", nullable = false)
    private String status;
}