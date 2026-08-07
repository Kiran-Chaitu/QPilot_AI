package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.AnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    Optional<AnalysisRun> findFirstByProjectIdOrderByStartedAtDesc(Long projectId);
}
