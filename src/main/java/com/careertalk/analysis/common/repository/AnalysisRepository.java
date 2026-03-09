package com.careertalk.analysis.common.repository;

import com.careertalk.analysis.common.entity.AnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisEntity, Long> {

    Optional<AnalysisEntity> findFirstByTargetIdOrderByAnalysisIdDesc(Long targetId);

    Optional<AnalysisEntity> findTopByUserNumAndTargetTypeAndTargetIdOrderByAnalyzedAtDesc(
            Long userNum, String targetType, Long targetId
    );

    List<AnalysisEntity> findByUserNumOrderByCreatedAtDesc(Long userNum);
}