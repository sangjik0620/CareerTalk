package com.careertalk.payment.repository;

import com.careertalk.payment.entity.PaymentProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentProductRepository extends JpaRepository<PaymentProduct, Long> {
    Optional<PaymentProduct> findByProductCodeAndActive(String productCode, String active);
    List<PaymentProduct> findAllByActiveOrderByProductIdAsc(String active);
}