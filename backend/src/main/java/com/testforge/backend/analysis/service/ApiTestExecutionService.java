package com.testforge.backend.analysis.service;

import com.testforge.backend.analysis.entity.GeneratedTest;
import com.testforge.backend.analysis.entity.TestExecutionStatus;
import com.testforge.backend.analysis.repository.GeneratedTestRepository;
import com.testforge.backend.project.entity.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Actually runs the executable tests QPilot generated, against the project's own configured target.
 *
 * <p>This service is the only thing in the codebase permitted to move a test out of
 * {@link TestExecutionStatus#GENERATED}, and it only does so after observing a real HTTP response or
 * a real transport failure. Every recorded pass carries the status code and measured latency that
 * justified it, so "31 passed" is a claim backed by 31 round-trips rather than by 31 rows existing.
 *
 * <p>Target selection is intentionally not caller-controlled: the base URL comes from the project the
 * user owns, never from the request body. That keeps this endpoint from being usable as an
 * SSRF/port-scanning primitive against arbitrary hosts.
 */
@Service
public class ApiTestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ApiTestExecutionService.class);

    private static final String USER_AGENT = "QPilot-AI-TestRunner/1.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);

    /**
     * Upper bound on requests issued per execution pass. Generated suites can be large and this runs
     * against someone's real service, so the pass is bounded rather than unbounded-by-suite-size.
     */
    private static final int MAX_TESTS_PER_RUN = 60;

    /** Politeness delay between requests so a test pass never resembles a load test. */
    private static final long INTER_REQUEST_DELAY_MS = 60;

    /** Methods QPilot will issue on its own initiative. DELETE is excluded — see {@link #executeAll}. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH");

    /** Path-template placeholder syntaxes: OpenAPI/Spring {@code {id}} and Express/Rails {@code :id}. */
    private static final Pattern BRACE_PARAM = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern COLON_PARAM = Pattern.compile("/:([A-Za-z_][A-Za-z0-9_]*)");

    private final GeneratedTestRepository generatedTestRepository;
    private final HttpClient httpClient;

    public ApiTestExecutionService(GeneratedTestRepository generatedTestRepository) {
        this.generatedTestRepository = generatedTestRepository;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Executes every executable test belonging to {@code project} and persists the real outcomes.
     *
     * @return the tests in their post-execution state
     */
    @Transactional
    public List<GeneratedTest> executeAll(Project project) {
        List<GeneratedTest> tests = generatedTestRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        String baseUrl = resolveBaseUrl(project);

        int issued = 0;
        for (GeneratedTest test : tests) {
            if (test.getRequestPath() == null || test.getRequestMethod() == null) {
                // Unit scaffolds and anything else without a concrete request stay untouched: their
                // NOT_EXECUTABLE status and reason were already set at generation time.
                continue;
            }
            if (baseUrl == null) {
                markSkipped(test, "No target URL is configured for this project, so there is nothing to send "
                        + "the request to. Add a target API URL or website URL to the project to enable execution.");
                continue;
            }
            if (!SAFE_METHODS.contains(test.getRequestMethod().toUpperCase())) {
                markSkipped(test, "QPilot does not issue " + test.getRequestMethod()
                        + " requests automatically, to avoid destructive calls against a live service. "
                        + "Run this test manually if the target is a disposable environment.");
                continue;
            }
            if (issued >= MAX_TESTS_PER_RUN) {
                markSkipped(test, "Execution cap of " + MAX_TESTS_PER_RUN + " requests per run was reached. "
                        + "Re-run execution to continue with the remaining tests.");
                continue;
            }
            executeOne(test, baseUrl);
            issued++;
            sleepBetweenRequests();
        }

        generatedTestRepository.saveAll(tests);
        log.info("Test execution pass for project {}: {} requests issued against {}",
                project.getId(), issued, baseUrl != null ? baseUrl : "(no target)");
        return tests;
    }

    // ─── Single test execution ───────────────────────────────────────────────────

    private void executeOne(GeneratedTest test, String baseUrl) {
        PathResolution resolved = resolvePathTemplate(test.getRequestPath());
        URI uri;
        try {
            uri = URI.create(baseUrl + resolved.path());
        } catch (IllegalArgumentException e) {
            markStatus(test, TestExecutionStatus.EXECUTION_ERROR, null, null,
                    "Could not build a valid URL from base '" + baseUrl + "' and path '"
                            + test.getRequestPath() + "': " + e.getMessage());
            return;
        }

        // nanoTime, not currentTimeMillis: latency is an interval measurement and must not be affected
        // by wall-clock adjustments (NTP steps, DST) occurring mid-request.
        long startNanos = System.nanoTime();
        try {
            HttpRequest request = buildRequest(uri, test);
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = elapsedMs(startNanos);

            Set<Integer> expected = parseExpectedStatuses(test.getExpectedStatusCodes());
            int observed = response.statusCode();
            boolean matched = expected.isEmpty() ? (observed >= 200 && observed < 400) : expected.contains(observed);

            markStatus(test,
                    matched ? TestExecutionStatus.EXECUTED_PASSED : TestExecutionStatus.EXECUTED_FAILED,
                    observed, latencyMs,
                    (matched ? "Passed: " : "Failed: ") + test.getRequestMethod() + " " + uri
                            + " returned HTTP " + observed + " in " + latencyMs + "ms. Expected "
                            + (expected.isEmpty() ? "any 2xx/3xx" : expected.toString()) + "."
                            + resolved.note());
        } catch (java.net.http.HttpTimeoutException e) {
            markStatus(test, TestExecutionStatus.EXECUTION_ERROR, null, elapsedMs(startNanos),
                    "Request timed out after " + REQUEST_TIMEOUT.toSeconds() + "s with no response from " + uri + ".");
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            markStatus(test, TestExecutionStatus.EXECUTION_ERROR, null, elapsedMs(startNanos),
                    "Could not reach " + uri + ": " + e.getClass().getSimpleName() + " — "
                            + (e.getMessage() != null ? e.getMessage() : "host unreachable")
                            + ". Verify the target is running and the URL is correct.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markStatus(test, TestExecutionStatus.EXECUTION_ERROR, null, elapsedMs(startNanos),
                    "Execution was interrupted before the response arrived.");
        } catch (Exception e) {
            markStatus(test, TestExecutionStatus.EXECUTION_ERROR, null, elapsedMs(startNanos),
                    "Request to " + uri + " failed: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    private HttpRequest buildRequest(URI uri, GeneratedTest test) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, */*")
                .timeout(REQUEST_TIMEOUT);

        String method = test.getRequestMethod().toUpperCase();
        String body = test.getRequestBody();

        if (body != null && !body.isBlank()) {
            builder.header("Content-Type", "application/json");
        }
        HttpRequest.BodyPublisher publisher = (body != null && !body.isBlank())
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

        return switch (method) {
            case "GET" -> builder.GET().build();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case "OPTIONS" -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            default -> builder.method(method, publisher).build();
        };
    }

    // ─── Status bookkeeping ──────────────────────────────────────────────────────

    private void markStatus(GeneratedTest test, TestExecutionStatus status, Integer httpStatus,
                            Long latencyMs, String detail) {
        test.setExecutionStatus(status);
        test.setObservedHttpStatus(httpStatus);
        test.setExecutionLatencyMs(latencyMs);
        test.setExecutionDetail(truncate(detail));
        test.setLastExecutedAt(Instant.now());
    }

    private void markSkipped(GeneratedTest test, String reason) {
        test.setExecutionStatus(TestExecutionStatus.SKIPPED);
        test.setExecutionDetail(truncate(reason));
        // Deliberately leaves lastExecutedAt/observedHttpStatus untouched — a skipped test produced no
        // observation, and stamping one would imply a measurement that never happened.
    }

    // ─── Path template resolution ────────────────────────────────────────────────

    /**
     * A concrete path plus a note describing any substitution made.
     *
     * @param note appended to the execution detail so the recorded result names the values used. Without
     *             it, a 404 against {@code /users/{id}} would look like a broken endpoint when it is
     *             really just a probe id that does not exist.
     */
    private record PathResolution(String path, String note) {
    }

    /**
     * Substitutes path-template placeholders with concrete probe values.
     *
     * <p>Route declarations carry templates — {@code /users/{id}}, {@code /posts/:slug} — and braces and
     * colons are not legal in a URI path, so sending the template verbatim fails at URI construction
     * before any request is made. Every templated endpoint therefore reported an execution error that
     * said nothing about the endpoint itself.
     *
     * <p>Values are chosen by parameter name: identifier-like parameters get {@code 1}, which is far more
     * likely to resolve to a real record than an arbitrary string, and anything else gets a harmless
     * token. The stored {@code requestPath} keeps the original template, since that is what documents
     * the route.
     */
    private PathResolution resolvePathTemplate(String path) {
        if (path == null) {
            return new PathResolution("/", "");
        }
        if (!path.contains("{") && !path.contains(":")) {
            return new PathResolution(path, "");
        }

        List<String> substitutions = new ArrayList<>();
        Matcher braceMatcher = BRACE_PARAM.matcher(path);
        StringBuilder result = new StringBuilder();
        while (braceMatcher.find()) {
            String name = braceMatcher.group(1);
            String value = probeValueFor(name);
            substitutions.add("{" + name + "}=" + value);
            braceMatcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        braceMatcher.appendTail(result);

        // Express/Rails-style ":param" segments, handled after braces so both syntaxes are covered.
        Matcher colonMatcher = COLON_PARAM.matcher(result.toString());
        StringBuilder finalPath = new StringBuilder();
        while (colonMatcher.find()) {
            String name = colonMatcher.group(1);
            String value = probeValueFor(name);
            substitutions.add(":" + name + "=" + value);
            colonMatcher.appendReplacement(finalPath, "/" + Matcher.quoteReplacement(value));
        }
        colonMatcher.appendTail(finalPath);

        String note = substitutions.isEmpty() ? ""
                : " Path parameters were substituted with probe values (" + String.join(", ", substitutions)
                        + "), so a 404 here may simply mean no such record exists.";
        return new PathResolution(finalPath.toString(), note);
    }

    private String probeValueFor(String parameterName) {
        String lower = parameterName.toLowerCase();
        if (lower.equals("id") || lower.endsWith("id") || lower.endsWith("index")
                || lower.contains("number") || lower.contains("count")) {
            return "1";
        }
        return "qpilot-probe";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Resolves the project's own base URL, preferring the API URL over the website URL. Returns null
     * when the project has neither, which the caller reports as SKIPPED rather than treating as failure.
     */
    private String resolveBaseUrl(Project project) {
        String candidate = firstNonBlank(project.getTargetApiUrl(), project.getTargetUrl());
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        // Paths are always absolute ("/users"), so a trailing slash on the base would yield "//users".
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Set<Integer> parseExpectedStatuses(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d{3}"))
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(INTER_REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 990 ? value.substring(0, 990) + "…" : value;
    }
}
