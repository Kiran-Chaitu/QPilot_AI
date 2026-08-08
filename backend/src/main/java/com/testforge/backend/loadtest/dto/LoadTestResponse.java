package com.testforge.backend.loadtest.dto;

import java.util.List;
import java.util.Map;

public record LoadTestResponse(
        String targetUrl,
        int vus,
        int durationSeconds,
        int rampUpSeconds,
        int rpsThroughput,
        long avgLatencyMs,
        long p50Ms,
        long p90Ms,
        long p95Ms,
        long p99Ms,
        long minLatencyMs,
        long maxLatencyMs,
        int totalRequests,
        int successfulRequests,
        int failedRequests,
        double successRatePercent,
        double errorRatePercent,
        Map<Integer, Integer> statusCodeDistribution,
        String rateLimitStatus,
        List<RateLimitPolicyItem> rateLimitPolicies,
        String k6Script,
        String jmeterScript
) {
    public record RateLimitPolicyItem(String policy, String value, String status) {}
}
