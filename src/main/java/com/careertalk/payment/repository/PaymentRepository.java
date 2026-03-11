package com.careertalk.payment.repository;

import com.careertalk.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPartnerOrderId(String partnerOrderId);
    Optional<Payment> findByPaymentIdAndUserNum(Long paymentId, Long userNum);
    List<Payment> findAllByUserNumOrderByCreatedAtDesc(Long userNum);
}