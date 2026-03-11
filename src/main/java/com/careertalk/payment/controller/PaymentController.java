package com.careertalk.payment.controller;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import com.careertalk.payment.dto.PaymentQuotaResponse;
import com.careertalk.payment.dto.PaymentProductResponse;
import com.careertalk.payment.dto.ProductPurchaseRequest;
import com.careertalk.payment.service.PaymentService;
import com.careertalk.payment.service.QuotaService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final QuotaService quotaService;
    private final MemberRepository memberRepository;

    @GetMapping("/products")
    public ResponseEntity<List<PaymentProductResponse>> getProducts() {
        return ResponseEntity.ok(paymentService.getProducts());
    }

    @GetMapping("/quota")
    public ResponseEntity<?> getQuota(Principal principal) {
        try {
            Long userNum = getUserNum(principal);
            PaymentQuotaResponse response = quotaService.getQuota(userNum);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/kakao/ready")
    public ResponseEntity<?> ready(
            @RequestBody ProductPurchaseRequest request,
            Principal principal
    ) {
        try {
            Long userNum = getUserNum(principal);
            var response = paymentService.ready(userNum, request.getProductCode());

            return ResponseEntity.ok(Map.of(
                    "redirectUrl", response.getNextRedirectPcUrl()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/kakao/success")
    public ResponseEntity<Void> success(
            @RequestParam("partnerOrderId") String partnerOrderId,
            @RequestParam("pg_token") String pgToken
    ) {
        paymentService.approve(partnerOrderId, pgToken);

        return ResponseEntity.status(302)
                .location(URI.create("http://localhost:5173/interview/select"))
                .build();
    }

    @GetMapping("/kakao/cancel")
    public ResponseEntity<Void> cancel(@RequestParam("partnerOrderId") String partnerOrderId) {
        paymentService.cancel(partnerOrderId);

        return ResponseEntity.status(302)
                .location(URI.create("http://localhost:5173/interview/select"))
                .build();
    }

    @GetMapping("/kakao/fail")
    public ResponseEntity<Void> fail(
            @RequestParam("partnerOrderId") String partnerOrderId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        paymentService.fail(partnerOrderId, reason);

        return ResponseEntity.status(302)
                .location(URI.create("http://localhost:5173/payment"))
                .build();
    }

    private Long getUserNum(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }

        String loginId = principal.getName();

        if (loginId == null || loginId.isBlank()) {
            throw new IllegalStateException("인증 정보가 올바르지 않습니다.");
        }

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("해당 로그인 사용자를 찾을 수 없습니다."));

        return member.getUserNum();
    }
}