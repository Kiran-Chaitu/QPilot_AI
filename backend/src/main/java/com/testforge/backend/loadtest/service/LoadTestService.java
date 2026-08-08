package com.testforge.backend.loadtest.service;

import com.testforge.backend.loadtest.dto.LoadTestRequest;
import com.testforge.backend.loadtest.dto.LoadTestResponse;
import com.testforge.backend.loadtest.dto.LoadTestResponse.RateLimitPolicyItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);
    private static final String USER_AGENT = "QPilot-AI-LoadTester/2.0";
    private static final int MAX_TOTAL_REQUESTS = 2000;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    public LoadTestResponse runLoadTest(LoadTestRequest req) {
        String url = normalizeUrl(req.targetUrl());
        String httpMethod = req.httpMethod() != null && !req.httpMethod().isBlank()
                ? req.httpMethod().toUpperCase() : "GET";

        int vus = Math.max(1, Math.min(500, req.vus()));
        int durationSeconds = Math.max(5, Math.min(120, req.durationSeconds()));
        int rampUpSeconds = Math.max(1, Math.min(30, req.rampUpSeconds()));

        // Calculate total requests: scale to keep test safe but meaningful
        // Each VU sends roughly 1 request per second for the test duration
        int totalPlanned = Math.min(MAX_TOTAL_REQUESTS, vus * durationSeconds);

        log.info("Starting real load test: url={}, method={}, vus={}, duration={}s, planned_requests={}",
                url, httpMethod, vus, durationSeconds, totalPlanned);

        // Execute real concurrent load test
        LoadTestMetrics metrics = executeLoadTest(url, httpMethod, vus, durationSeconds, rampUpSeconds, totalPlanned);

        // Compute real percentiles from collected latencies
        long[] sortedLatencies = metrics.latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        long avgLatency = sortedLatencies.length > 0
                ? (long) metrics.latencies.stream().mapToLong(Long::longValue).average().orElse(0) : 0;
        long p50 = percentile(sortedLatencies, 50);
        long p90 = percentile(sortedLatencies, 90);
        long p95 = percentile(sortedLatencies, 95);
        long p99 = percentile(sortedLatencies, 99);
        long minLatency = sortedLatencies.length > 0 ? sortedLatencies[0] : 0;
        long maxLatency = sortedLatencies.length > 0 ? sortedLatencies[sortedLatencies.length - 1] : 0;

        int totalRequests = metrics.totalRequests.get();
        int successfulRequests = metrics.successfulRequests.get();
        int failedRequests = totalRequests - successfulRequests;
        double actualDurationSec = Math.max(1.0, metrics.actualDurationMs / 1000.0);
        int rps = (int) Math.round(totalRequests / actualDurationSec);
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0;
        double errorRate = 100.0 - successRate;

        // Build real status code distribution
        Map<Integer, Integer> statusDist = new TreeMap<>();
        metrics.statusCodes.forEach((code, count) -> statusDist.put(code, count.get()));

        // Detect real rate limiting from actual responses
        String rateLimitStatus = detectRateLimitStatus(metrics);
        List<RateLimitPolicyItem> policies = buildRealRateLimitPolicies(metrics);

        String k6Script = buildK6Script(url, httpMethod, vus, durationSeconds, rampUpSeconds, p95);
        String jmeterScript = buildJMeterScript(url, httpMethod, vus, durationSeconds, rampUpSeconds);

        log.info("Load test completed: total={}, success={}, failed={}, rps={}, avgLatency={}ms, p95={}ms",
                totalRequests, successfulRequests, failedRequests, rps, avgLatency, p95);

        return new LoadTestResponse(
                url, vus, durationSeconds, rampUpSeconds, rps, avgLatency, p50, p90, p95, p99,
                minLatency, maxLatency, totalRequests, successfulRequests, failedRequests,
                successRate, errorRate, statusDist, rateLimitStatus, policies, k6Script, jmeterScript
        );
    }

    // ─── Real Load Test Execution Engine ─────────────────────────────────

    private LoadTestMetrics executeLoadTest(String url, String httpMethod, int vus,
                                            int durationSeconds, int rampUpSeconds, int totalPlanned) {
        LoadTestMetrics metrics = new LoadTestMetrics();
        long testStartMs = System.currentTimeMillis();

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Use a thread pool sized to the number of virtual users
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        // Ramp-up: gradually increase concurrent users
        long rampUpMs = rampUpSeconds * 1000L;
        long sustainMs = durationSeconds * 1000L;
        long totalTestMs = rampUpMs + sustainMs;

        AtomicInteger requestsSent = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int vuIndex = 0; vuIndex < vus; vuIndex++) {
            final int vuNum = vuIndex;
            // Stagger VU start times across the ramp-up period
            long vuStartDelay = rampUpMs > 0 ? (long) ((double) vuNum / vus * rampUpMs) : 0;

            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    // Wait for this VU's ramp-up slot
                    if (vuStartDelay > 0) {
                        Thread.sleep(vuStartDelay);
                    }

                    long vuEndTime = testStartMs + totalTestMs;
                    // Each VU keeps sending requests until time is up or max requests reached
                    while (System.currentTimeMillis() < vuEndTime
                            && requestsSent.get() < totalPlanned) {
                        if (requestsSent.incrementAndGet() > totalPlanned) break;

                        sendSingleRequest(httpClient, url, httpMethod, metrics);

                        // Small delay between requests from same VU (simulates think time)
                        Thread.sleep(50 + ThreadLocalRandom.current().nextInt(100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown(); // Start all VUs

        // Wait for all VUs to finish with a timeout
        executor.shutdown();
        try {
            executor.awaitTermination(totalTestMs + 30_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        metrics.actualDurationMs = System.currentTimeMillis() - testStartMs;
        return metrics;
    }

    private void sendSingleRequest(HttpClient httpClient, String url, String httpMethod,
                                   LoadTestMetrics metrics) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));

            switch (httpMethod) {
                case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody());
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }

            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            long latency = System.currentTimeMillis() - start;

            metrics.totalRequests.incrementAndGet();
            metrics.latencies.add(latency);
            metrics.statusCodes.computeIfAbsent(response.statusCode(), k -> new AtomicInteger(0)).incrementAndGet();

            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                metrics.successfulRequests.incrementAndGet();
            }

            // Capture real rate limit headers
            if (response.statusCode() == 429) {
                metrics.rateLimited429Count.incrementAndGet();
            }

            response.headers().firstValue("retry-after").ifPresent(v -> metrics.retryAfterValues.add(v));
            response.headers().firstValue("x-ratelimit-limit").ifPresent(v -> metrics.rateLimitHeader.set(v));
            response.headers().firstValue("x-ratelimit-remaining").ifPresent(v -> metrics.rateLimitRemaining.set(v));
            response.headers().firstValue("x-ratelimit-reset").ifPresent(v -> metrics.rateLimitReset.set(v));
            // Also check lowercase variants
            response.headers().firstValue("ratelimit-limit").ifPresent(v -> {
                if (metrics.rateLimitHeader.get() == null) metrics.rateLimitHeader.set(v);
            });
            response.headers().firstValue("ratelimit-remaining").ifPresent(v -> {
                if (metrics.rateLimitRemaining.get() == null) metrics.rateLimitRemaining.set(v);
            });

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            metrics.totalRequests.incrementAndGet();
            metrics.latencies.add(latency);
            metrics.statusCodes.computeIfAbsent(0, k -> new AtomicInteger(0)).incrementAndGet();
            log.trace("Request failed: {}", ex.getMessage());
        }
    }

    // ─── Rate Limit Detection from Real Response Data ────────────────────

    private String detectRateLimitStatus(LoadTestMetrics metrics) {
        int rateLimited = metrics.rateLimited429Count.get();
        int total = metrics.totalRequests.get();

        if (rateLimited > 0) {
            double pct = total > 0 ? (rateLimited * 100.0 / total) : 0;
            return String.format("429 Rate Limit Detected — %d/%d requests throttled (%.1f%%)",
                    rateLimited, total, pct);
        }

        if (metrics.rateLimitHeader.get() != null) {
            return "Rate Limit Headers Present — No Throttling Triggered";
        }

        return "No Rate Limiting Detected";
    }

    private List<RateLimitPolicyItem> buildRealRateLimitPolicies(LoadTestMetrics metrics) {
        List<RateLimitPolicyItem> policies = new ArrayList<>();

        int rateLimited = metrics.rateLimited429Count.get();

        // HTTP 429 status
        policies.add(new RateLimitPolicyItem(
                "HTTP 429 Responses",
                String.valueOf(rateLimited),
                rateLimited > 0 ? "Rate Limiting Active" : "Not Triggered"
        ));

        // X-RateLimit-Limit header
        String limitHeader = metrics.rateLimitHeader.get();
        policies.add(new RateLimitPolicyItem(
                "X-RateLimit-Limit",
                limitHeader != null ? limitHeader : "Not Present",
                limitHeader != null ? "Detected in Headers" : "Not Exposed"
        ));

        // X-RateLimit-Remaining header
        String remainingHeader = metrics.rateLimitRemaining.get();
        policies.add(new RateLimitPolicyItem(
                "X-RateLimit-Remaining",
                remainingHeader != null ? remainingHeader : "Not Present",
                remainingHeader != null ? "Detected in Headers" : "Not Exposed"
        ));

        // X-RateLimit-Reset header
        String resetHeader = metrics.rateLimitReset.get();
        policies.add(new RateLimitPolicyItem(
                "X-RateLimit-Reset",
                resetHeader != null ? resetHeader : "Not Present",
                resetHeader != null ? "Detected in Headers" : "Not Exposed"
        ));

        // Retry-After header
        Set<String> retryAfters = new HashSet<>(metrics.retryAfterValues);
        policies.add(new RateLimitPolicyItem(
                "Retry-After Header",
                retryAfters.isEmpty() ? "Not Present" : String.join(", ", retryAfters),
                retryAfters.isEmpty() ? "Not Exposed" : "Detected in Headers"
        ));

        // Status code distribution summary
        String statusSummary = metrics.statusCodes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " → " + e.getValue().get())
                .collect(Collectors.joining(", "));
        policies.add(new RateLimitPolicyItem(
                "Status Code Distribution",
                statusSummary.isEmpty() ? "No Data" : statusSummary,
                "Observed"
        ));

        return policies;
    }

    // ─── Metrics Collection ─────────────────────────────────────────────

    private static class LoadTestMetrics {
        final AtomicInteger totalRequests = new AtomicInteger(0);
        final AtomicInteger successfulRequests = new AtomicInteger(0);
        final AtomicInteger rateLimited429Count = new AtomicInteger(0);
        final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        final ConcurrentHashMap<Integer, AtomicInteger> statusCodes = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<String> retryAfterValues = new ConcurrentLinkedQueue<>();
        final java.util.concurrent.atomic.AtomicReference<String> rateLimitHeader = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> rateLimitRemaining = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> rateLimitReset = new java.util.concurrent.atomic.AtomicReference<>();
        volatile long actualDurationMs = 0;
    }

    // ─── Math Utilities ─────────────────────────────────────────────────

    private long percentile(long[] sortedValues, int percentile) {
        if (sortedValues.length == 0) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.length) - 1;
        return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return "https://example.com";
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    // ─── Script Generators ──────────────────────────────────────────────

    private String buildK6Script(String url, String method, int vus, int duration, int rampUp, long p95Threshold) {
        String httpCall = switch (method) {
            case "POST" -> "http.post('" + url + "', null)";
            case "PUT" -> "http.put('" + url + "', null)";
            case "DELETE" -> "http.del('" + url + "')";
            case "HEAD" -> "http.head('" + url + "')";
            default -> "http.get('" + url + "')";
        };

        return "import http from 'k6/http';\n"
                + "import { check, sleep } from 'k6';\n\n"
                + "export const options = {\n"
                + "  stages: [\n"
                + "    { duration: '" + rampUp + "s', target: " + vus + " },\n"
                + "    { duration: '" + duration + "s', target: " + vus + " },\n"
                + "    { duration: '5s', target: 0 },\n"
                + "  ],\n"
                + "  thresholds: {\n"
                + "    http_req_duration: ['p(95)<" + p95Threshold + "'],\n"
                + "    http_req_failed: ['rate<0.01'],\n"
                + "  },\n"
                + "};\n\n"
                + "export default function () {\n"
                + "  const res = " + httpCall + ";\n"
                + "  check(res, {\n"
                + "    'status is 200': (r) => r.status === 200,\n"
                + "    'transaction duration < " + p95Threshold + "ms': (r) => r.timings.duration < " + p95Threshold + ",\n"
                + "  });\n"
                + "  sleep(1);\n"
                + "}\n";
    }

    private String buildJMeterScript(String url, String method, int vus, int duration, int rampUp) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"QPilot Load Test Plan\">\n"
                + "      <elementProp name=\"ThreadGroup.main\" elementType=\"ThreadGroup\">\n"
                + "        <stringProp name=\"ThreadGroup.num_threads\">" + vus + "</stringProp>\n"
                + "        <stringProp name=\"ThreadGroup.ramp_time\">" + rampUp + "</stringProp>\n"
                + "        <stringProp name=\"ThreadGroup.duration\">" + duration + "</stringProp>\n"
                + "        <stringProp name=\"HTTPSampler.method\">" + method + "</stringProp>\n"
                + "        <stringProp name=\"Target.url\">" + url + "</stringProp>\n"
                + "      </elementProp>\n"
                + "    </TestPlan>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";
    }
}
