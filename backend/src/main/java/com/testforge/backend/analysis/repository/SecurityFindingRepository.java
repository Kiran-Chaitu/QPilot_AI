package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, Long> {
    List<SecurityFinding> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<SecurityFinding> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);
}
