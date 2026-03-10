package com.careertalk.payment.repository;

import com.careertalk.payment.entity.UsageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageHistoryRepository extends JpaRepository<UsageHistory, Long> {
}