package com.careertalk.analysis.coverletter.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_document")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiFileDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    private Long userId;

    private String originalName;

    private String storedPath;

    private Long fileSize;

    private LocalDateTime createdAt;
}
