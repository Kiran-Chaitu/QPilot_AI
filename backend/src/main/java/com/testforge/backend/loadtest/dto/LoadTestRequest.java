package com.testforge.backend.loadtest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Load-test configuration supplied by the client.
 *
 * <p>Bean-validation bounds here give the caller an immediate, specific error. They are a first line
 * of defence only — the server independently clamps every value to the operator-configured safety
 * envelope in {@code LoadTestProperties}, because a client is free to ignore the contract.
 *
 * @param authorizedTarget the caller's explicit confirmation that they own or are permitted to test
 *                         this target. Required by default: load testing someone else's
 *                         infrastructure is indistinguishable from an attack on it, so the
 *                         attestation is recorded rather than assumed.
 */
public record LoadTestRequest(
        @NotBlank(message = "Target URL is required")
        String targetUrl,

        @Pattern(regexp = "(?i)GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS",
                message = "HTTP method must be one of GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS")
        String httpMethod,

        @Min(value = 1, message = "At least 1 virtual user is required")
        @Max(value = 1000, message = "Virtual users cannot exceed 1000")
        int virtualUsers,

        @Min(value = 1, message = "Duration must be at least 1 second")
        @Max(value = 600, message = "Duration cannot exceed 600 seconds")
        int durationSeconds,

        @Min(value = 0, message = "Ramp-up cannot be negative")
        @Max(value = 300, message = "Ramp-up cannot exceed 300 seconds")
        int rampUpSeconds,

        @Min(value = 0, message = "Ramp-down cannot be negative")
        @Max(value = 300, message = "Ramp-down cannot exceed 300 seconds")
        int rampDownSeconds,

        /** Optional aggregate rate ceiling. When null, virtual users send as fast as responses allow. */
        @Min(value = 1, message = "Target requests/sec must be at least 1")
        Integer targetRequestsPerSecond,

        @Min(value = 1, message = "Timeout must be at least 1 second")
        @Max(value = 120, message = "Timeout cannot exceed 120 seconds")
        Integer requestTimeoutSeconds,

        Map<String, String> headers,

        String requestBody,

        Long projectId,

        boolean authorizedTarget
) {
}
