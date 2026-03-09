package com.careertalk.analysis.coverletter.repository;

import com.careertalk.analysis.coverletter.entity.CiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CiAnalysisRepository extends JpaRepository<CiAnalysis, Long> {

    List<CiAnalysis> findByUserNumOrderByCreatedAtDesc(Long userNum);

    Optional<CiAnalysis> findByAnalysisIdAndUserNum(Long analysisId, Long userNum);
}