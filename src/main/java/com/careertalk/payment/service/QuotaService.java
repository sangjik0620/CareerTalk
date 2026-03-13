package com.careertalk.payment.service;

import com.careertalk.payment.dto.PaymentQuotaResponse;
import com.careertalk.payment.entity.UserUsageQuota;
import com.careertalk.payment.entity.UsageHistory;
import com.careertalk.payment.enums.UsageSourceType;
import com.careertalk.payment.enums.UsageType;
import com.careertalk.payment.repository.UserUsageQuotaRepository;
import com.careertalk.payment.repository.UsageHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserUsageQuotaRepository quotaRepository;
    private final UsageHistoryRepository usageHistoryRepository;

    @Transactional(readOnly = true)
    public PaymentQuotaResponse getQuota(Long userNum) {
        UserUsageQuota quota = quotaRepository.findByUserNum(userNum)
                .orElseThrow(() -> new EntityNotFoundException("이용권 정보가 없습니다."));

        return PaymentQuotaResponse.builder()
                .freeAnalysisRemaining(quota.getFreeAnalysisRemaining())
                .freeMockRemaining(quota.getFreeMockRemaining())
                .paidAnalysisRemaining(quota.getPaidAnalysisRemaining())
                .paidMockRemaining(quota.getPaidMockRemaining())
                .build();
    }

    @Transactional
    public void grantCredits(Long userNum, int analysisCount, int mockCount) {
        UserUsageQuota quota = quotaRepository.findByUserNum(userNum)
                .orElseThrow(() -> new EntityNotFoundException("이용권 정보가 없습니다."));

        quota.setPaidAnalysisRemaining(quota.getPaidAnalysisRemaining() + analysisCount);
        quota.setPaidMockRemaining(quota.getPaidMockRemaining() + mockCount);
    }

    @Transactional
    public void consumeAnalysis(Long userNum, String refTable, Long refId, String description) {
        UserUsageQuota quota = quotaRepository.findByUserNum(userNum)
                .orElseThrow(() -> new EntityNotFoundException("이용권 정보가 없습니다."));

        String sourceType;
        if (quota.getFreeAnalysisRemaining() > 0) {
            quota.setFreeAnalysisRemaining(quota.getFreeAnalysisRemaining() - 1);
            sourceType = UsageSourceType.FREE.name();
        } else if (quota.getPaidAnalysisRemaining() > 0) {
            quota.setPaidAnalysisRemaining(quota.getPaidAnalysisRemaining() - 1);
            sourceType = UsageSourceType.PAID.name();
        } else {
            throw new IllegalStateException("분석 이용권이 없습니다.");
        }

        usageHistoryRepository.save(
                UsageHistory.builder()
                        .userNum(userNum)
                        .usageType(UsageType.ANALYSIS.name())
                        .sourceType(sourceType)
                        .refTable(refTable)
                        .refId(refId)
                        .description(description)
                        .build()
        );
    }

    @Transactional
    public void consumeMockInterview(Long userNum, String refTable, Long refId, String description) {
        UserUsageQuota quota = quotaRepository.findByUserNum(userNum)
                .orElseThrow(() -> new EntityNotFoundException("이용권 정보가 없습니다."));

        String sourceType;
        if (quota.getFreeMockRemaining() > 0) {
            quota.setFreeMockRemaining(quota.getFreeMockRemaining() - 1);
            sourceType = UsageSourceType.FREE.name();
        } else if (quota.getPaidMockRemaining() > 0) {
            quota.setPaidMockRemaining(quota.getPaidMockRemaining() - 1);
            sourceType = UsageSourceType.PAID.name();
        } else {
            throw new IllegalStateException("모의면접 이용권이 없습니다.");
        }

        usageHistoryRepository.save(
                UsageHistory.builder()
                        .userNum(userNum)
                        .usageType(UsageType.MOCK_INTERVIEW.name())
                        .sourceType(sourceType)
                        .refTable(refTable)
                        .refId(refId)
                        .description(description)
                        .build()
        );
    }

    @Transactional
    public void createInitialQuota(Long userNum) {
        quotaRepository.findByUserNum(userNum).ifPresent(q -> {
            throw new IllegalStateException("이미 이용권 정보가 존재합니다.");
        });

        quotaRepository.save(UserUsageQuota.builder()
                .userNum(userNum)
                .freeAnalysisRemaining(2)   // 원하는 기본값
                .freeMockRemaining(1)       // 원하는 기본값
                .paidAnalysisRemaining(0)
                .paidMockRemaining(0)
                .build());
    }
}