package com.careertalk.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "user_usage_quota")
public class UserUsageQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quota_id")
    private Long quotaId;

    @Column(name = "user_num", nullable = false, unique = true)
    private Long userNum;

    @Column(name = "free_analysis_remaining", nullable = false)
    private Integer freeAnalysisRemaining;

    @Column(name = "free_mock_remaining", nullable = false)
    private Integer freeMockRemaining;

    @Column(name = "paid_analysis_remaining", nullable = false)
    private Integer paidAnalysisRemaining;

    @Column(name = "paid_mock_remaining", nullable = false)
    private Integer paidMockRemaining;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}