package com.careertalk.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "user_num", nullable = false)
    private Long userNum;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "partner_order_id", nullable = false, unique = true, length = 100)
    private String partnerOrderId;

    @Column(name = "partner_user_id", nullable = false, length = 100)
    private String partnerUserId;

    @Column(name = "tid", length = 100)
    private String tid;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "tax_free_amount", nullable = false)
    private Integer taxFreeAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "pg_token", length = 255)
    private String pgToken;

    @Column(name = "ready_requested_at")
    private LocalDateTime readyRequestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}