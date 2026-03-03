package com.careertalk.analysis.resume.repository;

import com.careertalk.analysis.resume.entity.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {
}
