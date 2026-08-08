package com.testforge.backend.analysis.dto;

import java.time.Instant;
import java.util.List;

/**
 * Computed risk/quality assessment exposed to the frontend.
 *
 * <p>There is deliberately no "code coverage" field. {@code testedSurfacePercent} is a different
 * measurement and {@code testedSurfaceBasis} states in plain English exactly what was counted, so the
 * UI can label the number accurately instead of implying executed line coverage QPilot never measured.
 * {@code scoreBreakdown} carries the arithmetic behind {@code score}, and {@code unavailableChecks}
 * names what could not be assessed and why.
 */
public record RiskAssessmentResponse(
        int score,
        List<String> reasons,
        List<String> scoreBreakdown,
        int testedSurfacePercent,
        String testedSurfaceBasis,
        List<String> coverageGaps,
        List<String> unavailableChecks,
        MeasuredCounts measured,
        Instant createdAt
) {
    /** The raw counts the score was derived from, so the frontend can show inputs beside the output. */
    public record MeasuredCounts(
            int sourceFileCount,
            int testFileCount,
            long totalLinesOfCode,
            int endpointCount,
            int endpointsReferencedByTests,
            int criticalFindingCount,
            int highFindingCount,
            int mediumFindingCount,
            int lowFindingCount
    ) {
    }
}
