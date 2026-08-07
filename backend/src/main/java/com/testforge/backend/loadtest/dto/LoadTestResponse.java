package com.testforge.backend.loadtest.dto;

import java.util.List;

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
        double successRatePercent,
        double errorRatePercent,
        String rateLimitStatus,
        List<RateLimitPolicyItem> rateLimitPolicies,
        String k6Script,
        String jmeterScript
) {
    public record RateLimitPolicyItem(String policy, String value, String status) {}
}
