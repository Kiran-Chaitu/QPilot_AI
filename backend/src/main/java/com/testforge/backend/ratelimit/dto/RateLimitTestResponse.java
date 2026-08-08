package com.testforge.backend.ratelimit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a rate-limit probe, expressed strictly in terms of what was observed.
 *
 * <p>{@code rateLimitingDetected} is true only when the target actually throttled a request (HTTP 429)
 * or advertised a limit via headers. When nothing was observed, {@code verdict} says the probe found no
 * evidence <em>at the load applied</em> — it never claims the target has no rate limiting, because a
 * probe that stayed under the threshold cannot establish that.
 *
 * @param burst      results of the fast-burst phase
 * @param sustained  results of the paced phase, or null when the caller skipped it
 * @param evidence   the raw observations the verdict was derived from
 */
public record RateLimitTestResponse(
        String targetUrl,
        String httpMethod,
        boolean rateLimitingDetected,
        String verdict,
        PhaseResult burst,
        PhaseResult sustained,
        Evidence evidence,
        List<String> notes,
        long totalDurationMs,
        Instant executedAt
) {

    /**
     * One probe phase.
     *
     * @param firstThrottledAtRequest 1-based index of the first request answered with 429, or null if
     *                                none were — this is the closest thing to an observed threshold
     * @param observedRequestsPerSec  the rate actually achieved, which may be below the requested rate
     */
    public record PhaseResult(
            String phase,
            int requestsSent,
            int successCount,
            int throttled429Count,
            int otherErrorCount,
            Integer firstThrottledAtRequest,
            double observedRequestsPerSec,
            long avgLatencyMs,
            long durationMs,
            Map<Integer, Integer> statusDistribution
    ) {
    }

    /**
     * Raw rate-limit signals captured from response headers.
     *
     * <p>Each field is null when the target did not send that header. Null means "not advertised", which
     * is reported as such rather than defaulted to a number that would look like a real limit.
     */
    public record Evidence(
            String rateLimitLimit,
            String rateLimitRemaining,
            String rateLimitReset,
            List<String> retryAfterValues,
            boolean retryAfterHonoured,
            List<String> allRateLimitHeaderNames
    ) {
    }
}
