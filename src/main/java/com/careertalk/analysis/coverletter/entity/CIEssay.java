package com.careertalk.analysis.coverletter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "essays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CIEssay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "essay_id")
    private Long essayId;

    @Column(name = "user_num", nullable = false)
    private Long userNum; // users.user_num 저장

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "title", length = 200)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_type", length = 20)
    private String sourceType; // TEXT / FILE

    @Column(name = "status", length = 20)
    private String status; // SAVED / DRAFT

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}