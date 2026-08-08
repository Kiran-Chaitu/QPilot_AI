package com.testforge.backend.e2etest.service;

import com.testforge.backend.e2etest.dto.E2eTestRequest;
import com.testforge.backend.e2etest.dto.E2eTestResponse;
import com.testforge.backend.e2etest.dto.E2eTestResponse.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs HTTP-based E2E smoke tests on a target website.
 * <p>
 * Checks include: page accessibility, login flow simulation, authenticated page access,
 * internal link crawling, API health endpoints, and security header validation.
 * Also generates a downloadable Playwright test script for the user's specific flow.
 */
@Service
public class E2eTestService {

    private static final Logger log = LoggerFactory.getLogger(E2eTestService.class);
    private static final String USER_AGENT = "QPilot-AI-E2ETester/2.0";

    private static final String[] SECURITY_HEADERS = {
            "Strict-Transport-Security",
            "Content-Security-Policy",
            "X-Content-Type-Options",
            "X-Frame-Options",
            "X-XSS-Protection",
            "Referrer-Policy",
            "Permissions-Policy"
    };

    private static final String[] API_HEALTH_PATHS = {
            "/api/health", "/api/status", "/health", "/actuator/health", "/api/v1/health"
    };

    private final HttpClient httpClient;

    public E2eTestService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public E2eTestResponse runE2eTest(E2eTestRequest req) {
        long startTime = System.currentTimeMillis();
        String targetUrl = normalizeUrl(req.targetUrl());
        List<TestResult> results = new ArrayList<>();
        String authToken = null;
        Map<String, String> authCookies = new HashMap<>();

        // 1. Page Accessibility Check
        results.add(checkPageAccessibility(targetUrl));

        // 2. Login Flow Simulation (if login URL provided)
        if (req.loginUrl() != null && !req.loginUrl().isBlank()) {
            String loginUrl = normalizeUrl(req.loginUrl());
            TestResult loginResult = simulateLoginFlow(loginUrl, req.username(), req.password());
            results.add(loginResult);

            if (loginResult.passed() && loginResult.details() != null) {
                authToken = loginResult.details();
            }
        }

        // 3. Authenticated Page Access (if we got an auth token)
        if (authToken != null) {
            results.add(checkAuthenticatedAccess(targetUrl, authToken));
        }

        // 4. Link Crawling — discover and test internal navigation links
        results.addAll(crawlInternalLinks(targetUrl));

        // 5. API Health Check — test common API endpoints
        results.addAll(checkApiHealthEndpoints(targetUrl));

        // 6. CORS/Security Header Check
        results.addAll(checkSecurityHeaders(targetUrl));

        int passed = (int) results.stream().filter(TestResult::passed).count();
        int failed = results.size() - passed;
        long executionTime = System.currentTimeMillis() - startTime;

        String playwrightScript = generatePlaywrightScript(req, results);

        return new E2eTestResponse(
                targetUrl, results.size(), passed, failed,
                results, playwrightScript, executionTime
        );
    }

    // ─── Individual Check Methods ───────────────────────────────────────

    private TestResult checkPageAccessibility(String url) {
        try {
            long start = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
            return new TestResult(
                    "Page Accessibility",
                    "CONNECTIVITY",
                    ok,
                    response.statusCode(),
                    latency,
                    ok ? "Page returned HTTP " + response.statusCode() + " successfully" : null,
                    ok ? null : "Page returned HTTP " + response.statusCode()
            );
        } catch (Exception ex) {
            return new TestResult(
                    "Page Accessibility", "CONNECTIVITY", false, 0, 0,
                    null, "Connection failed: " + ex.getMessage()
            );
        }
    }

    private TestResult simulateLoginFlow(String loginUrl, String username, String password) {
        try {
            long start = System.currentTimeMillis();

            // Build form/JSON body for login
            String jsonBody = String.format(
                    "{\"email\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}",
                    username != null ? username : "",
                    username != null ? username : "",
                    password != null ? password : ""
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
            String token = extractToken(response.body());
            String authHeader = response.headers().firstValue("Authorization").orElse(null);
            if (token == null && authHeader != null) {
                token = authHeader;
            }

            return new TestResult(
                    "Login Flow Simulation",
                    "AUTHENTICATION",
                    isSuccess,
                    response.statusCode(),
                    latency,
                    isSuccess ? (token != null ? token : "Login returned 2xx") : null,
                    isSuccess ? null : "Login returned HTTP " + response.statusCode() + ": " + truncate(response.body(), 200)
            );
        } catch (Exception ex) {
            return new TestResult(
                    "Login Flow Simulation", "AUTHENTICATION", false, 0, 0,
                    null, "Login request failed: " + ex.getMessage()
            );
        }
    }

    private TestResult checkAuthenticatedAccess(String targetUrl, String authToken) {
        try {
            long start = System.currentTimeMillis();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            // Try Bearer token format
            if (!authToken.toLowerCase().startsWith("bearer ")) {
                builder.header("Authorization", "Bearer " + authToken);
            } else {
                builder.header("Authorization", authToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
            return new TestResult(
                    "Authenticated Page Access",
                    "AUTHENTICATION",
                    ok,
                    response.statusCode(),
                    latency,
                    ok ? "Protected page accessible with auth token (HTTP " + response.statusCode() + ")" : null,
                    ok ? null : "Authenticated access returned HTTP " + response.statusCode()
            );
        } catch (Exception ex) {
            return new TestResult(
                    "Authenticated Page Access", "AUTHENTICATION", false, 0, 0,
                    null, "Authenticated request failed: " + ex.getMessage()
            );
        }
    }

    private List<TestResult> crawlInternalLinks(String targetUrl) {
        List<TestResult> results = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Extract href links from HTML
            Set<String> links = extractLinks(body, targetUrl);
            int checked = 0;
            for (String link : links) {
                if (checked >= 20) break; // Limit to 20 links to keep response time reasonable
                try {
                    long start = System.currentTimeMillis();
                    HttpRequest linkReq = HttpRequest.newBuilder()
                            .uri(URI.create(link))
                            .header("User-Agent", USER_AGENT)
                            .timeout(Duration.ofSeconds(5))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<Void> linkResp = httpClient.send(linkReq, HttpResponse.BodyHandlers.discarding());
                    long latency = System.currentTimeMillis() - start;

                    // Fallback to GET if HEAD returns 405 Method Not Allowed
                    if (linkResp.statusCode() == 405) {
                        start = System.currentTimeMillis();
                        HttpRequest getReq = HttpRequest.newBuilder()
                                .uri(URI.create(link))
                                .header("User-Agent", USER_AGENT)
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();
                        HttpResponse<Void> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
                        latency = System.currentTimeMillis() - start;
                        boolean ok = getResp.statusCode() >= 200 && getResp.statusCode() < 400;
                        results.add(new TestResult(
                                "Internal Link: " + truncateUrl(link),
                                "NAVIGATION",
                                ok,
                                getResp.statusCode(),
                                latency,
                                ok ? "Link reachable (GET fallback)" : null,
                                ok ? null : "Broken link — HTTP " + getResp.statusCode()
                        ));
                    } else {
                        boolean ok = linkResp.statusCode() >= 200 && linkResp.statusCode() < 400;
                        results.add(new TestResult(
                                "Internal Link: " + truncateUrl(link),
                                "NAVIGATION",
                                ok,
                                linkResp.statusCode(),
                                latency,
                                ok ? "Link reachable" : null,
                                ok ? null : "Broken link — HTTP " + linkResp.statusCode()
                        ));
                    }
                    checked++;
                } catch (Exception ex) {
                    results.add(new TestResult(
                            "Internal Link: " + truncateUrl(link),
                            "NAVIGATION", false, 0, 0,
                            null, "Link unreachable: " + ex.getMessage()
                    ));
                    checked++;
                }
            }

            if (links.isEmpty()) {
                results.add(new TestResult(
                        "Link Crawling",
                        "NAVIGATION",
                        true,
                        response.statusCode(),
                        0,
                        "No internal links found to crawl (page may be an SPA)",
                        null
                ));
            }
        } catch (Exception ex) {
            results.add(new TestResult(
                    "Link Crawling", "NAVIGATION", false, 0, 0,
                    null, "Could not crawl page: " + ex.getMessage()
            ));
        }
        return results;
    }

    private List<TestResult> checkApiHealthEndpoints(String targetUrl) {
        List<TestResult> results = new ArrayList<>();
        URI baseUri = URI.create(targetUrl);
        String baseUrl = baseUri.getScheme() + "://" + baseUri.getAuthority();

        for (String path : API_HEALTH_PATHS) {
            try {
                String healthUrl = baseUrl + path;
                long start = System.currentTimeMillis();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long latency = System.currentTimeMillis() - start;

                boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
                results.add(new TestResult(
                        "API Health: " + path,
                        "API_HEALTH",
                        ok,
                        response.statusCode(),
                        latency,
                        ok ? "Endpoint responded with HTTP " + response.statusCode() : null,
                        ok ? null : "Endpoint returned HTTP " + response.statusCode()
                ));
            } catch (Exception ex) {
                results.add(new TestResult(
                        "API Health: " + path,
                        "API_HEALTH",
                        false,
                        0,
                        0,
                        null,
                        "Endpoint unreachable: " + ex.getMessage()
                ));
            }
        }
        return results;
    }

    private List<TestResult> checkSecurityHeaders(String targetUrl) {
        List<TestResult> results = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            for (String header : SECURITY_HEADERS) {
                Optional<String> value = response.headers().firstValue(header.toLowerCase());
                boolean present = value.isPresent();
                results.add(new TestResult(
                        "Security Header: " + header,
                        "SECURITY",
                        present,
                        response.statusCode(),
                        0,
                        present ? header + ": " + value.get() : null,
                        present ? null : "Missing security header: " + header
                ));
            }
        } catch (Exception ex) {
            results.add(new TestResult(
                    "Security Headers Check", "SECURITY", false, 0, 0,
                    null, "Could not check security headers: " + ex.getMessage()
            ));
        }
        return results;
    }

    // ─── Playwright Script Generator ────────────────────────────────────

    private String generatePlaywrightScript(E2eTestRequest req, List<TestResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("import { test, expect } from '@playwright/test';\n\n");
        sb.append("/**\n");
        sb.append(" * QPilot AI — Auto-Generated E2E Playwright Test Script\n");
        sb.append(" * Target: ").append(req.targetUrl()).append("\n");
        sb.append(" * Generated: ").append(java.time.Instant.now()).append("\n");
        sb.append(" */\n\n");

        // Test 1: Page loads
        sb.append("test('Target page loads successfully', async ({ page }) => {\n");
        sb.append("  const response = await page.goto('").append(req.targetUrl()).append("');\n");
        sb.append("  expect(response?.status()).toBeLessThan(400);\n");
        sb.append("  await expect(page).not.toHaveTitle('');\n");
        sb.append("});\n\n");

        // Test 2: Login flow (if login URL provided)
        if (req.loginUrl() != null && !req.loginUrl().isBlank()) {
            sb.append("test('User can log in successfully', async ({ page }) => {\n");
            sb.append("  await page.goto('").append(req.loginUrl()).append("');\n\n");
            sb.append("  // Fill login form — adjust selectors to match your actual login page\n");
            if (req.username() != null) {
                sb.append("  await page.fill('input[type=\"email\"], input[name=\"username\"], input[name=\"email\"]', '")
                        .append(req.username()).append("');\n");
            }
            if (req.password() != null) {
                sb.append("  await page.fill('input[type=\"password\"]', '")
                        .append(req.password()).append("');\n");
            }
            sb.append("  await page.click('button[type=\"submit\"]');\n\n");
            sb.append("  // Wait for navigation after login\n");
            sb.append("  await page.waitForURL((url) => !url.href.includes('login'), { timeout: 10000 });\n");
            sb.append("  const currentUrl = page.url();\n");
            sb.append("  expect(currentUrl).not.toContain('login');\n");
            sb.append("});\n\n");

            // Test 3: Authenticated navigation
            sb.append("test('Authenticated user can access protected pages', async ({ page }) => {\n");
            sb.append("  // Login first\n");
            sb.append("  await page.goto('").append(req.loginUrl()).append("');\n");
            if (req.username() != null) {
                sb.append("  await page.fill('input[type=\"email\"], input[name=\"username\"], input[name=\"email\"]', '")
                        .append(req.username()).append("');\n");
            }
            if (req.password() != null) {
                sb.append("  await page.fill('input[type=\"password\"]', '")
                        .append(req.password()).append("');\n");
            }
            sb.append("  await page.click('button[type=\"submit\"]');\n");
            sb.append("  await page.waitForURL((url) => !url.href.includes('login'), { timeout: 10000 });\n\n");
            sb.append("  // Navigate to target page\n");
            sb.append("  await page.goto('").append(req.targetUrl()).append("');\n");
            sb.append("  const response = await page.waitForLoadState('networkidle');\n");
            sb.append("  expect(page.url()).not.toContain('login');\n");
            sb.append("});\n\n");
        }

        // Test 4: Security headers
        sb.append("test('Security headers are properly configured', async ({ request }) => {\n");
        sb.append("  const response = await request.get('").append(req.targetUrl()).append("');\n");
        sb.append("  const headers = response.headers();\n\n");
        sb.append("  // Check critical security headers\n");
        sb.append("  expect(headers['x-content-type-options']).toBe('nosniff');\n");
        sb.append("  expect(headers['x-frame-options']).toBeTruthy();\n");
        sb.append("});\n\n");

        // Test 5: No console errors
        sb.append("test('Page has no critical console errors', async ({ page }) => {\n");
        sb.append("  const errors: string[] = [];\n");
        sb.append("  page.on('console', (msg) => {\n");
        sb.append("    if (msg.type() === 'error') errors.push(msg.text());\n");
        sb.append("  });\n\n");
        sb.append("  await page.goto('").append(req.targetUrl()).append("');\n");
        sb.append("  await page.waitForLoadState('networkidle');\n\n");
        sb.append("  // Allow some errors but flag critical ones\n");
        sb.append("  const criticalErrors = errors.filter((e) =>\n");
        sb.append("    !e.includes('favicon') && !e.includes('404')\n");
        sb.append("  );\n");
        sb.append("  expect(criticalErrors.length).toBeLessThanOrEqual(2);\n");
        sb.append("});\n");

        return sb.toString();
    }

    // ─── Utility Methods ────────────────────────────────────────────────

    private String normalizeUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    private String extractToken(String responseBody) {
        if (responseBody == null) return null;
        // Try common JSON patterns: "token": "...", "accessToken": "...", "access_token": "..."
        Pattern tokenPattern = Pattern.compile("\"(?:token|accessToken|access_token|jwt)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = tokenPattern.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Set<String> extractLinks(String html, String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        URI baseUri = URI.create(baseUrl);
        String baseHost = baseUri.getHost();

        // Match href="..." patterns
        Pattern hrefPattern = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = hrefPattern.matcher(html);

        while (matcher.find()) {
            String href = matcher.group(1).trim();
            // Skip fragments, javascript, mailto, tel
            if (href.startsWith("#") || href.startsWith("javascript:") ||
                    href.startsWith("mailto:") || href.startsWith("tel:")) {
                continue;
            }

            try {
                URI resolved;
                if (href.startsWith("http://") || href.startsWith("https://")) {
                    resolved = URI.create(href);
                } else if (href.startsWith("/")) {
                    resolved = URI.create(baseUri.getScheme() + "://" + baseUri.getAuthority() + href);
                } else {
                    continue; // Skip relative paths without leading /
                }

                // Only include links on the same host
                if (resolved.getHost() != null && resolved.getHost().equalsIgnoreCase(baseHost)) {
                    links.add(resolved.toString());
                }
            } catch (Exception ignored) {
                // Skip malformed URLs
            }
        }

        return links;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }

    private String truncateUrl(String url) {
        if (url == null) return "";
        if (url.length() <= 60) return url;
        return url.substring(0, 57) + "…";
    }
}
