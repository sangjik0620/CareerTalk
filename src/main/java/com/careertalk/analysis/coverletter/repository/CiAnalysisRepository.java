package com.careertalk.analysis.coverletter.repository;

import com.careertalk.analysis.coverletter.entity.CiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CiAnalysisRepository extends JpaRepository<CiAnalysis, Long> {

    Optional<CiAnalysis> findFirstByTargetIdOrderByAnalysisIdDesc(Long targetId);

}
