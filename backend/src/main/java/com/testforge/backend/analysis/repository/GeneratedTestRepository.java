package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.GeneratedTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedTestRepository extends JpaRepository<GeneratedTest, Long> {
    List<GeneratedTest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<GeneratedTest> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);
}
