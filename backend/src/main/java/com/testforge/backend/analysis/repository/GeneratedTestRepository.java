package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.GeneratedTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GeneratedTestRepository extends JpaRepository<GeneratedTest, Long> {
    List<GeneratedTest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<GeneratedTest> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);

    @Query("SELECT COUNT(t) FROM GeneratedTest t WHERE t.project.owner.id = :userId")
    long countByOwner(Long userId);

    @Query("SELECT t.type, COUNT(t) FROM GeneratedTest t WHERE t.project.owner.id = :userId GROUP BY t.type")
    List<Object[]> countByTypeGrouped(Long userId);
}
