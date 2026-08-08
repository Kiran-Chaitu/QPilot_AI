package com.testforge.backend.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated dashboard statistics, every field backed by a database aggregate over real rows.
 *
 * <p>Test counts are split deliberately. {@code totalTestsGenerated} is how many tests exist;
 * {@code testsExecuted}/{@code testsPassed}/{@code testsFailed} come from execution records written
 * only after a real HTTP round-trip. A workspace with 120 generated tests and no reachable target
 * therefore reports 120 generated and 0 executed, instead of implying 120 passing tests.
 *
 * @param riskHistory      chronological risk/tested-surface points from stored assessments — the source
 *                         of the trend chart, so it plots real history rather than a synthetic curve
 * @param loadTestSummary  aggregate of real completed load-test runs, or null when none have been run
 */
public record DashboardStatsResponse(
        int totalProjects,
        int analyzedProjects,
        int totalTestsGenerated,
        int testsExecuted,
        int testsPassed,
        int testsFailed,
        int testsErrored,
        int testsNotExecutable,
        int totalSecurityFindings,
        int criticalFindings,
        int highFindings,
        int mediumFindings,
        int lowFindings,
        Double avgTestedSurfacePercent,
        Double avgRiskScore,
        List<TestTypeCount> testDistribution,
        List<SecurityAdvice> topAdvice,
        List<RiskPoint> riskHistory,
        LoadTestSummary loadTestSummary
) {
    public record TestTypeCount(String type, long count) {
    }

    public record SecurityAdvice(String category, String severity, String description, String recommendation,
                                 String origin, String location) {
    }

    /** One stored risk assessment, for the trend chart. */
    public record RiskPoint(String projectName, int riskScore, int testedSurfacePercent, Instant recordedAt) {
    }

    /** Aggregate over completed load-test runs the user has actually executed. */
    public record LoadTestSummary(int completedRuns, long totalRequests, double avgResponseTimeMs,
                                  double avgErrorRatePercent, Instant lastRunAt) {
    }
}
