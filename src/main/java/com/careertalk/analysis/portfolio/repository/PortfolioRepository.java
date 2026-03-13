package com.careertalk.analysis.portfolio.repository;

import com.careertalk.analysis.portfolio.entity.PortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
    void deleteByUserNum(Long userNum);
}