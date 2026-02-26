package com.careertalk.analysis.commonrepository;

import com.careertalk.analysis.commonentity.AnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisEntity, Long> {

    Optional<AnalysisEntity> findByTargetTypeAndTargetId(String targetType, Long targetId);

    Optional<AnalysisEntity> findFirstByTargetIdOrderByAnalysisIdDesc(Long targetId);
}