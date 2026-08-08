package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, Long> {
    List<SecurityFinding> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<SecurityFinding> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);

    @Query("SELECT COUNT(f) FROM SecurityFinding f WHERE f.project.owner.id = :userId")
    long countByOwner(Long userId);

    @Query("SELECT f FROM SecurityFinding f WHERE f.project.owner.id = :userId ORDER BY f.createdAt DESC")
    List<SecurityFinding> findTopByOwner(Long userId);
}
