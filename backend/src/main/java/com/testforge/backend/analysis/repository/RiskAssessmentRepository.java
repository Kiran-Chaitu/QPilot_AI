package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findByAnalysisRunId(Long analysisRunId);

    Optional<RiskAssessment> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Query("SELECT AVG(r.coverageEstimatePercent) FROM RiskAssessment r WHERE r.project.owner.id = :userId")
    Double avgCoverageByOwner(Long userId);

    @Query("SELECT AVG(r.score) FROM RiskAssessment r WHERE r.project.owner.id = :userId")
    Double avgRiskScoreByOwner(Long userId);
}
