package com.testforge.backend.ratelimit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Configuration for a rate-limit probe.
 *
 * <p>The probe runs two phases against the target: a short burst (many requests as fast as possible, to
 * trip a burst/token-bucket limiter) followed by a paced sustained phase (to trip a
 * requests-per-window limiter). Both are needed because a target can enforce one and not the other.
 *
 * @param authorizedTarget explicit attestation that the caller may test this target — required for the
 *                         same reason as load testing, since a burst probe is deliberately abusive traffic
 */
public record RateLimitTestRequest(
        @NotBlank(message = "Target URL is required")
        String targetUrl,

        @Pattern(regexp = "(?i)GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS",
                message = "HTTP method must be one of GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS")
        String httpMethod,

        @Min(value = 5, message = "Burst size must be at least 5 to be meaningful")
        @Max(value = 500, message = "Burst size cannot exceed 500")
        int burstRequests,

        @Min(value = 0, message = "Sustained request count cannot be negative")
        @Max(value = 500, message = "Sustained request count cannot exceed 500")
        int sustainedRequests,

        @Min(value = 1, message = "Sustained rate must be at least 1 request/sec")
        @Max(value = 100, message = "Sustained rate cannot exceed 100 requests/sec")
        int sustainedRequestsPerSecond,

        Map<String, String> headers,

        String requestBody,

        boolean authorizedTarget
) {
}
