package com.careertalk.payment.repository;

import com.careertalk.payment.entity.UserUsageQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserUsageQuotaRepository extends JpaRepository<UserUsageQuota, Long> {
    Optional<UserUsageQuota> findByUserNum(Long userNum);
}