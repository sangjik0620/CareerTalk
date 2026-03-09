package com.careertalk.analysis.portfolio.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "portfolios")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "status", length = 20)
    private String status;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    @Builder
    public PortfolioEntity(Long userNum, Long fileId, String title, String status, String extractedText) {
        this.userNum = userNum;
        this.fileId = fileId;
        this.title = title;
        this.status = status;
        this.extractedText = extractedText;
    }
}