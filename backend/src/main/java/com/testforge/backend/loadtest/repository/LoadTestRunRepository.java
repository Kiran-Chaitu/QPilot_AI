package com.testforge.backend.loadtest.repository;

import com.testforge.backend.loadtest.entity.LoadTestRun;
import com.testforge.backend.loadtest.entity.LoadTestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoadTestRunRepository extends JpaRepository<LoadTestRun, Long> {

    Optional<LoadTestRun> findByIdAndOwnerId(Long id, Long ownerId);

    List<LoadTestRun> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    int countByOwnerIdAndStatusIn(Long ownerId, List<LoadTestStatus> statuses);

    /**
     * Aggregate over the user's finished runs for the dashboard. Includes CANCELLED alongside COMPLETED
     * because a cancelled run still produced real measurements for the traffic it did send — excluding
     * it would discard genuine data.
     */
    @Query("""
            SELECT COUNT(r), COALESCE(SUM(r.totalRequests), 0), COALESCE(AVG(r.avgLatencyMs), 0),
                   COALESCE(AVG(r.errorRatePercent), 0), MAX(r.completedAt)
            FROM LoadTestRun r
            WHERE r.owner.id = :ownerId
              AND r.status IN (com.testforge.backend.loadtest.entity.LoadTestStatus.COMPLETED,
                               com.testforge.backend.loadtest.entity.LoadTestStatus.CANCELLED)
              AND r.totalRequests > 0
            """)
    List<Object[]> aggregateFinishedRuns(@Param("ownerId") Long ownerId);

    /** Runs left RUNNING by a process that died — reconciled to FAILED on the next startup. */
    List<LoadTestRun> findByStatus(LoadTestStatus status);
}
