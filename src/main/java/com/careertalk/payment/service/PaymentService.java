package com.careertalk.payment.service;

import com.careertalk.payment.dto.KakaoPayReadyResponse;
import com.careertalk.payment.dto.PaymentProductResponse;
import com.careertalk.payment.entity.Payment;
import com.careertalk.payment.entity.PaymentProduct;
import com.careertalk.payment.enums.PaymentStatus;
import com.careertalk.payment.repository.PaymentProductRepository;
import com.careertalk.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final KakaoPayService kakaoPayService;
    private final QuotaService quotaService;

    @Transactional(readOnly = true)
    public List<PaymentProductResponse> getProducts() {
        return productRepository.findAllByActiveOrderByProductIdAsc("ACTIVE")
                .stream()
                .map(p -> PaymentProductResponse.builder()
                        .productId(p.getProductId())
                        .productCode(p.getProductCode())
                        .productName(p.getProductName())
                        .productType(p.getProductType())
                        .price(p.getPrice())
                        .analysisCreditCount(p.getAnalysisCreditCount())
                        .mockCreditCount(p.getMockCreditCount())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Transactional
    public KakaoPayReadyResponse ready(Long userNum, String productCode) {
        PaymentProduct product = productRepository.findByProductCodeAndActive(productCode, "ACTIVE")
                .orElseThrow(() -> new EntityNotFoundException("상품이 없습니다."));

        String partnerOrderId = "CT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String partnerUserId = String.valueOf(userNum);

        Payment payment = Payment.builder()
                .userNum(userNum)
                .productId(product.getProductId())
                .provider("KAKAOPAY")
                .paymentType(product.getProductType())
                .partnerOrderId(partnerOrderId)
                .partnerUserId(partnerUserId)
                .itemName(product.getProductName())
                .quantity(1)
                .totalAmount(product.getPrice())
                .taxFreeAmount(0)
                .status(PaymentStatus.READY.name())
                .readyRequestedAt(LocalDateTime.now())
                .build();

        KakaoPayReadyResponse readyResponse = kakaoPayService.ready(
                partnerOrderId,
                partnerUserId,
                product.getProductName(),
                1,
                product.getPrice(),
                0
        );

        payment.setTid(readyResponse.getTid());
        paymentRepository.save(payment);

        return readyResponse;
    }

    @Transactional
    public void approve(String partnerOrderId, String pgToken) {
        Payment payment = paymentRepository.findByPartnerOrderId(partnerOrderId)
                .orElseThrow(() -> new EntityNotFoundException("결제 정보를 찾을 수 없습니다."));

        if (PaymentStatus.APPROVED.name().equals(payment.getStatus())) {
            return;
        }

        kakaoPayService.approve(
                payment.getTid(),
                payment.getPartnerOrderId(),
                payment.getPartnerUserId(),
                pgToken
        );

        payment.setPgToken(pgToken);
        payment.setStatus(PaymentStatus.APPROVED.name());
        payment.setApprovedAt(LocalDateTime.now());

        PaymentProduct product = productRepository.findById(payment.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("상품 정보를 찾을 수 없습니다."));

        quotaService.grantCredits(
                payment.getUserNum(),
                product.getAnalysisCreditCount(),
                product.getMockCreditCount()
        );
    }

    @Transactional
    public void cancel(String partnerOrderId) {
        Payment payment = paymentRepository.findByPartnerOrderId(partnerOrderId)
                .orElseThrow(() -> new EntityNotFoundException("결제 정보를 찾을 수 없습니다."));
        payment.setStatus(PaymentStatus.CANCELLED.name());
        payment.setCancelledAt(LocalDateTime.now());
    }

    @Transactional
    public void fail(String partnerOrderId, String reason) {
        Payment payment = paymentRepository.findByPartnerOrderId(partnerOrderId)
                .orElseThrow(() -> new EntityNotFoundException("결제 정보를 찾을 수 없습니다."));
        payment.setStatus(PaymentStatus.FAILED.name());
        payment.setFailedAt(LocalDateTime.now());
        payment.setFailReason(reason);
    }
}