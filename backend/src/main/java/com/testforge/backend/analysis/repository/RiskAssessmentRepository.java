package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findByAnalysisRunId(Long analysisRunId);

    Optional<RiskAssessment> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
}
