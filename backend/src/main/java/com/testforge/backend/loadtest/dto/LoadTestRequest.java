package com.testforge.backend.loadtest.dto;

public record LoadTestRequest(
        String targetUrl,
        int vus,
        int durationSeconds,
        int rampUpSeconds,
        String httpMethod
) {
}
