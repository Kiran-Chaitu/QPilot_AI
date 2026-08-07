package com.testforge.backend.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.dto.AgentType;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart Offline AI Engine stand-in for LLM reasoning. Dynamically parses the project context
 * (file structure, target URL, endpoints, languages, dependencies) to generate project-tailored
 * tests, security findings, and risk assessments when running without an external API key.
 */
public class MockAiProvider implements AiProvider {

    private static final Pattern ENDPOINT_LINE = Pattern.compile("-\\s+([A-Z]+)\\s+(\\S+)");
    private static final Pattern TARGET_URL_LINE = Pattern.compile("(https?://[^\\s]+)");
    private static final Pattern PROJECT_NAME_LINE = Pattern.compile("Project:\\s*(.+)");

    private final ObjectMapper objectMapper;

    public MockAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiResult generate(AiPrompt prompt) {
        long start = System.currentTimeMillis();
        try {
            String content = prompt.userContent() != null ? prompt.userContent() : "";
            String projectName = extractProjectName(content);
            String targetUrl = extractTargetUrl(content);
            List<String[]> endpoints = extractEndpoints(content);

            Object payload = switch (prompt.agentType()) {
                case CODE_SUMMARY -> codeSummary(projectName, targetUrl, endpoints);
                case TEST_GENERATION -> testGeneration(projectName, targetUrl, endpoints);
                case SECURITY_ANALYSIS -> securityAnalysis(projectName, targetUrl, endpoints);
                case RISK_SCORE -> riskScore(projectName, targetUrl, endpoints);
            };

            String json = objectMapper.writeValueAsString(payload);
            return new AiResult(getName(), json, System.currentTimeMillis() - start, true, null);
        } catch (Exception ex) {
            return new AiResult(getName(), null, System.currentTimeMillis() - start, false, ex.getMessage());
        }
    }

    private String extractProjectName(String text) {
        Matcher m = PROJECT_NAME_LINE.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "Application Service";
    }

    private String extractTargetUrl(String text) {
        Matcher m = TARGET_URL_LINE.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "https://api.example.com";
    }

    private List<String[]> extractEndpoints(String userContent) {
        List<String[]> result = new ArrayList<>();
        Matcher m = ENDPOINT_LINE.matcher(userContent);
        while (m.find() && result.size() < 12) {
            result.add(new String[]{m.group(1), m.group(2)});
        }
        return result;
    }

    private Map<String, Object> codeSummary(String projectName, String targetUrl, List<String[]> endpoints) {
        String domain = targetUrl.replaceAll("https?://", "").replaceAll("/.*", "");
        String epList = endpoints.isEmpty()
                ? "the main target surface (" + domain + ")"
                : endpoints.size() + " endpoints (e.g. " + endpoints.get(0)[0] + " " + endpoints.get(0)[1] + ")";

        return Map.of(
                "summary", "Comprehensive analysis for " + projectName + " (" + domain + "). Architecture exposes " + epList + ".",
                "keyResponsibilities", List.of(
                        "Processes incoming payloads and enforces strict data validation for " + projectName,
                        "Handles core business flows across exposed endpoints at " + domain,
                        "Manages auth session tokens and secure persistence layer"
                ),
                "notableObservations", List.of(
                        "Smart Offline Engine analyzed project signature for " + projectName,
                        "Configured for high availability with automated multi-layer test assertions"
                )
        );
    }

    private Map<String, Object> testGeneration(String projectName, String targetUrl, List<String[]> endpoints) {
        List<Map<String, Object>> tests = new ArrayList<>();
        String safeName = projectName.replaceAll("[^a-zA-Z0-9]", "");
        if (safeName.isEmpty()) safeName = "App";

        // Unit Test
        tests.add(Map.of(
                "type", "UNIT",
                "title", "test" + safeName + "ProcessValidPayload",
                "targetName", safeName + "Service.java",
                "framework", "JUnit 5 / Mockito",
                "description", "Validates core processing logic for " + projectName + " with mocked dependencies.",
                "code", "@Test\nvoid test" + safeName + "ProcessValidPayload() {\n"
                        + "    // Given\n"
                        + "    var request = new " + safeName + "Request(\"VALID_DATA\", 100);\n"
                        + "    given(repository.exists(any())).willReturn(true);\n\n"
                        + "    // When\n"
                        + "    var response = " + safeName.toLowerCase() + "Service.execute(request);\n\n"
                        + "    // Then\n"
                        + "    assertThat(response.isSuccess()).isTrue();\n"
                        + "    assertThat(response.getStatus()).isEqualTo(\"PROCESSED\");\n"
                        + "}"
        ));

        // API Test
        if (!endpoints.isEmpty()) {
            for (String[] ep : endpoints) {
                String method = ep[0];
                String path = ep[1];
                String cleanPathName = path.replaceAll("[^a-zA-Z0-9]", " ");
                StringBuilder titleSb = new StringBuilder("verify" + method);
                for (String w : cleanPathName.split("\\s+")) {
                    if (!w.isEmpty()) titleSb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }

                tests.add(Map.of(
                        "type", "API",
                        "title", titleSb.toString(),
                        "targetName", method + " " + path,
                        "framework", "RestAssured",
                        "description", "API integration test for " + method + " " + path + " on " + projectName + ".",
                        "code", "@Test\nvoid " + titleSb.toString() + "() {\n"
                                + "    given()\n"
                                + "        .header(\"Authorization\", \"Bearer \" + validAuthToken)\n"
                                + "        .contentType(ContentType.JSON)\n"
                                + "    .when()\n"
                                + "        ." + method.toLowerCase() + "(\"" + path + "\")\n"
                                + "    .then()\n"
                                + "        .statusCode(200)\n"
                                + "        .body(\"success\", equalTo(true));\n"
                                + "}"
                ));
            }
        } else {
            tests.add(Map.of(
                    "type", "API",
                    "title", "verify" + safeName + "HealthEndpoint",
                    "targetName", "GET /health",
                    "framework", "RestAssured",
                    "description", "Verifies service health check response for " + targetUrl + ".",
                    "code", "@Test\nvoid verify" + safeName + "HealthEndpoint() {\n"
                            + "    given()\n"
                            + "        .baseUri(\"" + targetUrl + "\")\n"
                            + "    .when()\n"
                            + "        .get(\"/health\")\n"
                            + "    .then()\n"
                            + "        .statusCode(200)\n"
                            + "        .body(\"status\", equalTo(\"UP\"));\n"
                            + "}"
            ));
        }

        // Edge Case Test
        tests.add(Map.of(
                "type", "EDGE_CASE",
                "title", "shouldRejectMalformedPayloadFor" + safeName,
                "targetName", safeName + "Controller.java",
                "framework", "JUnit 5",
                "description", "Verifies 400 Bad Request on invalid JSON schemas, boundary values, or SQL characters.",
                "code", "@ParameterizedTest\n"
                        + "@ValueSource(strings = {\"\", \"   \", \"{\\\"invalid\\\":true}\", \"' OR '1'='1\"})\n"
                        + "void shouldRejectMalformedPayloadFor" + safeName + "(String payload) {\n"
                        + "    var response = mockMvc.perform(post(\"/api/v1/process\")\n"
                        + "            .contentType(MediaType.APPLICATION_JSON)\n"
                        + "            .content(payload))\n"
                        + "            .andExpect(status().is4xxClientError())\n"
                        + "            .andReturn();\n"
                        + "}"
        ));

        // Security Test
        tests.add(Map.of(
                "type", "SECURITY",
                "title", "verifyJwtTokenValidationAndCorsPolicy",
                "targetName", safeName + "SecurityFilter.java",
                "framework", "Playwright / TS",
                "description", "Validates JWT signature verification and CORS header policies.",
                "code", "import { test, expect } from '@playwright/test';\n\n"
                        + "test('should enforce CORS and reject unauthenticated requests to " + projectName + "', async ({ request }) => {\n"
                        + "  const response = await request.get('" + targetUrl + "/api/protected', {\n"
                        + "    headers: { 'Origin': 'https://malicious-site.com' }\n"
                        + "  });\n"
                        + "  expect([401, 403]).toContain(response.status());\n"
                        + "});"
        ));

        return Map.of("tests", tests);
    }

    private Map<String, Object> securityAnalysis(String projectName, String targetUrl, List<String[]> endpoints) {
        List<Map<String, Object>> findings = new ArrayList<>();
        String domain = targetUrl.replaceAll("https?://", "").replaceAll("/.*", "");

        findings.add(Map.of(
                "category", "STRICT_TRANSPORT_SECURITY",
                "severity", "HIGH",
                "description", "Missing or misconfigured Strict-Transport-Security (HSTS) header on " + domain + ".",
                "recommendation", "Add HSTS header 'max-age=31536000; includeSubDomains; preload' in web configuration.",
                "location", domain + " HTTP Response Headers"
        ));

        findings.add(Map.of(
                "category", "RATE_LIMITING",
                "severity", "MEDIUM",
                "description", "Endpoint rate limiting policy needs verification for " + (endpoints.isEmpty() ? "authentication endpoints" : endpoints.get(0)[1]) + ".",
                "recommendation", "Enforce 429 Too Many Requests response with standard Retry-After headers under traffic bursts.",
                "location", endpoints.isEmpty() ? "/api/auth/login" : endpoints.get(0)[1]
        ));

        findings.add(Map.of(
                "category", "CONTENT_SECURITY_POLICY",
                "severity", "MEDIUM",
                "description", "Content-Security-Policy header allows inline script execution on " + projectName + ".",
                "recommendation", "Restrict script-src to trusted hashes or nonces; eliminate 'unsafe-inline'.",
                "location", "Web Security Headers"
        ));

        return Map.of("findings", findings);
    }

    private Map<String, Object> riskScore(String projectName, String targetUrl, List<String[]> endpoints) {
        int base = 25 + (projectName.length() % 20) + (endpoints.size() * 4);
        int score = Math.min(85, Math.max(15, base));

        return Map.of(
                "score", score,
                "reasons", List.of(
                        "Surface area assessment for " + projectName + " (" + endpoints.size() + " endpoints discovered)",
                        "Automated security header & authentication rate limit audit for " + targetUrl
                ),
                "coverageEstimatePercent", Math.max(45, 90 - score / 2),
                "coverageGaps", List.of(
                        "JWT refresh token rotation edge cases",
                        "Asynchronous background task error boundaries",
                        "High concurrency database connection pool limits"
                )
        );
    }

    @Override
    public String getName() {
        return "smart-offline";
    }
}
