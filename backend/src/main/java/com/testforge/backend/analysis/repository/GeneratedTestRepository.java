package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.GeneratedTest;
import com.testforge.backend.analysis.entity.TestExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GeneratedTestRepository extends JpaRepository<GeneratedTest, Long> {
    List<GeneratedTest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<GeneratedTest> findByAnalysisRunIdOrderByCreatedAtDesc(Long analysisRunId);

    List<GeneratedTest> findByProjectIdAndExecutionStatusIn(Long projectId, List<TestExecutionStatus> statuses);

    /**
     * Removes this project's rows from a previous analysis run.
     *
     * <p>Explicitly transactional: Spring Data does not wrap derived delete queries in a transaction
     * automatically, so without this the call fails at runtime with TransactionRequiredException.
     */
    @Transactional
    @Modifying
    void deleteByProjectId(Long projectId);

    @Query("SELECT COUNT(t) FROM GeneratedTest t WHERE t.project.owner.id = :userId")
    long countByOwner(@Param("userId") Long userId);

    @Query("SELECT t.type, COUNT(t) FROM GeneratedTest t WHERE t.project.owner.id = :userId GROUP BY t.type")
    List<Object[]> countByTypeGrouped(@Param("userId") Long userId);

    /**
     * Counts per execution status across the user's projects. The dashboard needs these to report
     * "tests executed / passed / failed" from real execution records instead of inferring them from
     * the number of tests that merely exist.
     */
    @Query("SELECT t.executionStatus, COUNT(t) FROM GeneratedTest t WHERE t.project.owner.id = :userId GROUP BY t.executionStatus")
    List<Object[]> countByExecutionStatusGrouped(@Param("userId") Long userId);
}
