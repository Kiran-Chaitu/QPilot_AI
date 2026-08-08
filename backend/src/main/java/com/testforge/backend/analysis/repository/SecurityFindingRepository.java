package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.SecurityFinding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, Long> {
    List<SecurityFinding> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<SecurityFinding> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);

    /**
     * Removes this project's rows from a previous analysis run.
     *
     * <p>Explicitly transactional: Spring Data does not wrap derived delete queries in a transaction
     * automatically, so without this the call fails at runtime with TransactionRequiredException.
     */
    @Transactional
    @Modifying
    void deleteByProjectId(Long projectId);

    @Query("SELECT COUNT(f) FROM SecurityFinding f WHERE f.project.owner.id = :userId")
    long countByOwner(@Param("userId") Long userId);

    @Query("SELECT f.severity, COUNT(f) FROM SecurityFinding f WHERE f.project.owner.id = :userId GROUP BY f.severity")
    List<Object[]> countBySeverityGrouped(@Param("userId") Long userId);

    /**
     * Highest-severity-first findings for the dashboard advice panel. Takes a {@link Pageable} so the
     * caller's "top N" is enforced by the database's LIMIT rather than by loading every finding the
     * user has ever produced and trimming in memory.
     */
    @Query("""
            SELECT f FROM SecurityFinding f
            WHERE f.project.owner.id = :userId
            ORDER BY CASE f.severity
                       WHEN com.testforge.backend.analysis.entity.Severity.CRITICAL THEN 0
                       WHEN com.testforge.backend.analysis.entity.Severity.HIGH THEN 1
                       WHEN com.testforge.backend.analysis.entity.Severity.MEDIUM THEN 2
                       ELSE 3
                     END, f.createdAt DESC
            """)
    List<SecurityFinding> findTopByOwner(@Param("userId") Long userId, Pageable pageable);
}
