package com.careertalk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentProductResponse {
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private Integer price;
    private Integer analysisCreditCount;
    private Integer mockCreditCount;
    private String description;
}