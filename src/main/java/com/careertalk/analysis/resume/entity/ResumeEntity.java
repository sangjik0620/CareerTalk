package com.careertalk.analysis.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "resumes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)          // ✅ 추가: @CreatedDate, @LastModifiedDate 동작에 필수
public class ResumeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "resume_title", nullable = false, length = 200)
    private String resumeTitle;

    @Column(name = "status", length = 20)
    private String status;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder                                             // ✅ 추가: id·날짜 제외한 필드만 받는 빌더
    public ResumeEntity(Long userNum, Long fileId, String resumeTitle, String status) {
        this.userNum = userNum;
        this.fileId = fileId;
        this.resumeTitle = resumeTitle;
        this.status = status;
    }
}