package com.testforge.backend.loadtest.dto;

import com.testforge.backend.loadtest.entity.LoadTestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A load test's configuration and measured results.
 *
 * <p>While {@code status} is RUNNING every metric is a live partial measurement of the traffic sent so
 * far, and {@code progressPercent} says how far through the plan the run is. Nothing is projected or
 * extrapolated: a run that has issued 300 requests reports 300, not an estimate of where it will end up.
 *
 * @param clampNotes  values the server reduced to fit its safety envelope, so the displayed
 *                    configuration always matches what actually ran
 * @param timeSeries  per-second measurements, the source of the run's charts
 */
public record LoadTestResponse(
        Long id,
        LoadTestStatus status,
        int progressPercent,
        String targetUrl,
        String httpMethod,
        int virtualUsers,
        int durationSeconds,
        int rampUpSeconds,
        int rampDownSeconds,
        Integer targetRequestsPerSecond,
        int requestTimeoutSeconds,
        String clampNotes,

        int totalRequests,
        int successfulRequests,
        int failedRequests,
        double successRatePercent,
        double errorRatePercent,
        double requestsPerSecond,
        long avgLatencyMs,
        long minLatencyMs,
        long maxLatencyMs,
        long p50LatencyMs,
        long p90LatencyMs,
        long p95LatencyMs,
        long p99LatencyMs,
        long actualDurationMs,
        long totalBytesReceived,

        Map<Integer, Integer> statusCodeDistribution,
        List<TimeSeriesPoint> timeSeries,
        RateLimitEvidence rateLimitEvidence,

        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    /**
     * One second of the run.
     *
     * @param secondOffset  seconds since the run started
     * @param requests      requests that completed during this second
     * @param errors        of those, how many were failures
     * @param avgLatencyMs  mean latency of the requests in this bucket
     * @param p95LatencyMs  95th percentile within this bucket
     */
    public record TimeSeriesPoint(
            int secondOffset,
            int requests,
            int errors,
            long avgLatencyMs,
            long p95LatencyMs
    ) {
    }

    /**
     * Observed rate-limiting evidence. {@code detected} is true only when the target actually returned
     * 429s or exposed RateLimit headers — the absence of evidence is reported as "no evidence", never
     * as a confirmed absence of rate limiting, because a run below the threshold proves nothing.
     */
    public record RateLimitEvidence(
            boolean detected,
            String verdict,
            int http429Count,
            List<String> retryAfterValues,
            String rateLimitLimitHeader,
            String rateLimitRemainingHeader,
            String rateLimitResetHeader
    ) {
    }
}
