package com.testforge.backend.e2etest.dto;

import java.util.List;

/**
 * Request payload for the E2E browser smoke test endpoint.
 */
public record E2eTestRequest(
        String targetUrl,
        String loginUrl,
        String username,
        String password,
        List<String> testScenarios
) {}
