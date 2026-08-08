package com.testforge.backend.dashboard.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated dashboard statistics across all of a user's projects.
 */
public record DashboardStatsResponse(
        int totalProjects,
        int analyzedProjects,
        int totalTestsGenerated,
        int totalSecurityFindings,
        double avgCoveragePercent,
        double avgRiskScore,
        List<TestTypeCount> testDistribution,
        List<SecurityAdvice> topAdvice
) {
    public record TestTypeCount(String type, long count) {}
    public record SecurityAdvice(String category, String severity, String description, String recommendation) {}
}
