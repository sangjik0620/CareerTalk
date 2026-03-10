package com.careertalk.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "payment_products")
public class PaymentProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "product_type", nullable = false, length = 30)
    private String productType;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "analysis_credit_count", nullable = false)
    private Integer analysisCreditCount;

    @Column(name = "mock_credit_count", nullable = false)
    private Integer mockCreditCount;

    @Column(name = "active", nullable = false, length = 20)
    private String active;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}