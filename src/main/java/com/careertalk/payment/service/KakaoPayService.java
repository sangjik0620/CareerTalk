package com.careertalk.payment.service;

import com.careertalk.payment.dto.KakaoPayApproveResponse;
import com.careertalk.payment.dto.KakaoPayReadyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayService {

    @Value("${kakao.pay.secret-key}")
    private String secretKey;

    @Value("${kakao.pay.cid}")
    private String cid;

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    @Value("${app.back-base-url}")
    private String backBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public KakaoPayReadyResponse ready(
            String partnerOrderId,
            String partnerUserId,
            String itemName,
            int quantity,
            int totalAmount,
            int taxFreeAmount
    ) {
        try {
            log.info("secretKey length={}", secretKey == null ? 0 : secretKey.trim().length());
            log.info("secretKey startsWithDEV={}", secretKey != null && secretKey.trim().startsWith("DEV"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "SECRET_KEY " + secretKey.trim());
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("cid", cid);
            body.put("partner_order_id", partnerOrderId);
            body.put("partner_user_id", partnerUserId);
            body.put("item_name", itemName);
            body.put("quantity", quantity);
            body.put("total_amount", totalAmount);
            body.put("tax_free_amount", taxFreeAmount);
            body.put("approval_url", backBaseUrl + "/api/payments/kakao/success?partnerOrderId=" + partnerOrderId);
            body.put("cancel_url", backBaseUrl + "/api/payments/kakao/cancel?partnerOrderId=" + partnerOrderId);
            body.put("fail_url", backBaseUrl + "/api/payments/kakao/fail?partnerOrderId=" + partnerOrderId);

            log.info("requestBody={}", body);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<KakaoPayReadyResponse> response = restTemplate.postForEntity(
                    "https://open-api.kakaopay.com/online/v1/payment/ready",
                    request,
                    KakaoPayReadyResponse.class
            );

            return response.getBody();

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("kakaopay ready error status={}", e.getStatusCode());
            log.error("kakaopay ready error body={}", e.getResponseBodyAsString(), e);
            throw e;
        }
    }

    public KakaoPayApproveResponse approve(
            String tid,
            String partnerOrderId,
            String partnerUserId,
            String pgToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("cid", cid);
        body.put("tid", tid);
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("pg_token", pgToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<KakaoPayApproveResponse> response = restTemplate.postForEntity(
                "https://open-api.kakaopay.com/online/v1/payment/approve",
                request,
                KakaoPayApproveResponse.class
        );

        return response.getBody();
    }

    public String getFrontBaseUrl() {
        return frontBaseUrl;
    }

    public void setFrontBaseUrl(String frontBaseUrl) {
        this.frontBaseUrl = frontBaseUrl;
    }
}