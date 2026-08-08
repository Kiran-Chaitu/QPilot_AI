package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findByAnalysisRunId(Long analysisRunId);

    Optional<RiskAssessment> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    /**
     * Removes this project's rows from a previous analysis run.
     *
     * <p>Explicitly transactional: Spring Data does not wrap derived delete queries in a transaction
     * automatically, so without this the call fails at runtime with TransactionRequiredException.
     */
    @Transactional
    @Modifying
    void deleteByProjectId(Long projectId);

    /**
     * Averages only the latest assessment per project. Averaging every historical row would let a
     * project that has been re-analyzed ten times outweigh one analyzed once, so the dashboard number
     * would drift with re-run counts rather than reflecting the current state of the portfolio.
     */
    @Query("""
            SELECT AVG(r.testedSurfacePercent) FROM RiskAssessment r
            WHERE r.project.owner.id = :userId
              AND r.createdAt = (SELECT MAX(r2.createdAt) FROM RiskAssessment r2 WHERE r2.project.id = r.project.id)
            """)
    Double avgTestedSurfaceByOwner(@Param("userId") Long userId);

    @Query("""
            SELECT AVG(r.score) FROM RiskAssessment r
            WHERE r.project.owner.id = :userId
              AND r.createdAt = (SELECT MAX(r2.createdAt) FROM RiskAssessment r2 WHERE r2.project.id = r.project.id)
            """)
    Double avgRiskScoreByOwner(@Param("userId") Long userId);

    /** Latest assessment per project, newest first — the source of the dashboard risk trend chart. */
    @Query("""
            SELECT r FROM RiskAssessment r
            WHERE r.project.owner.id = :userId
            ORDER BY r.createdAt ASC
            """)
    List<RiskAssessment> findAllByOwnerChronologically(@Param("userId") Long userId);
}
