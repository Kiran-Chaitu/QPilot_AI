package com.testforge.backend.e2etest.dto;

import java.util.List;

/**
 * Response payload for the E2E browser smoke test endpoint.
 */
public record E2eTestResponse(
        String targetUrl,
        int totalChecks,
        int passedChecks,
        int failedChecks,
        List<TestResult> testResults,
        String generatedPlaywrightScript,
        long executionTimeMs
) {
    public record TestResult(
            String checkName,
            String category,
            boolean passed,
            int httpStatus,
            long responseTimeMs,
            String details,
            String errorMessage
    ) {}
}
