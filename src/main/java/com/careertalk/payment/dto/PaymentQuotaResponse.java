package com.careertalk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentQuotaResponse {
    private int freeAnalysisRemaining;
    private int freeMockRemaining;
    private int paidAnalysisRemaining;
    private int paidMockRemaining;
}