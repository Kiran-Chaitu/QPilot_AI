package com.testforge.backend.loadtest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.config.AsyncJobLauncher;
import com.testforge.backend.config.LoadTestProperties;
import com.testforge.backend.loadtest.dto.LoadTestRequest;
import com.testforge.backend.loadtest.dto.LoadTestResponse;
import com.testforge.backend.loadtest.entity.LoadTestRun;
import com.testforge.backend.loadtest.entity.LoadTestStatus;
import com.testforge.backend.loadtest.repository.LoadTestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes real HTTP load tests and records what was actually measured.
 *
 * <h2>Why this is asynchronous</h2>
 * A run lasts as long as the user configured — up to two minutes by default. The previous design did
 * the work inline in the controller, so the browser and any intermediate proxy were held open for the
 * whole run and routinely timed out before results came back; there was also no way to stop a run once
 * started. Now {@link #startRun} validates, persists and returns a run id immediately, the traffic is
 * generated on a background thread, and clients poll {@link #getRun} for live partial metrics.
 *
 * <h2>Why the numbers are trustworthy</h2>
 * Every metric comes from a completed request: latency from {@code System.nanoTime()} deltas,
 * percentiles from the full sorted sample, throughput from the measured elapsed time rather than the
 * configured duration, and the status distribution from observed response codes (with 0 marking a
 * transport failure). Nothing is modelled, interpolated or randomly generated.
 *
 * <h2>Safety</h2>
 * This feature emits real traffic at a real host, so it is bounded on several independent axes — virtual
 * users, duration, aggregate rate and absolute request count — all clamped server-side to
 * {@link LoadTestProperties} regardless of what the client sends. Runs are also capped per user, and
 * unless the operator disables it, the caller must explicitly attest that they are authorized to test
 * the target.
 */
@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);

    private static final String USER_AGENT = "QPilot-AI-LoadTester/3.0";
    private static final int MAX_HISTORY_RESULTS = 25;

    /** Headers a caller may not override, because they identify the tool or would break measurement. */
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host", "connection", "content-length", "upgrade", "transfer-encoding");

    private final LoadTestRunRepository runRepository;
    private final LoadTestProperties properties;
    private final ObjectMapper objectMapper;
    private final AsyncJobLauncher asyncJobLauncher;

    /** Live state for in-flight runs, keyed by run id. Entries are removed when a run finishes. */
    private final ConcurrentHashMap<Long, LoadTestExecution> activeRuns = new ConcurrentHashMap<>();

    public LoadTestService(LoadTestRunRepository runRepository, LoadTestProperties properties,
                           ObjectMapper objectMapper, AsyncJobLauncher asyncJobLauncher) {
        this.runRepository = runRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.asyncJobLauncher = asyncJobLauncher;
    }

    /**
     * Reconciles runs that were RUNNING when the process last stopped. Their in-memory state died with
     * that process, so leaving them RUNNING would show a permanently-stuck progress bar that can never
     * be stopped.
     *
     * <p>Bound to ApplicationReadyEvent rather than {@code @PostConstruct}: during construction the
     * transactional proxy for this bean does not exist yet, so {@code @Transactional} would be silently
     * ignored and the writes below would run without a transaction.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileOrphanedRuns() {
        List<LoadTestRun> orphaned = runRepository.findByStatus(LoadTestStatus.RUNNING);
        for (LoadTestRun run : orphaned) {
            run.setStatus(LoadTestStatus.FAILED);
            run.setErrorMessage("The server restarted while this run was in progress, so it was terminated. "
                    + "Any metrics shown cover only the traffic recorded before the restart.");
            run.setCompletedAt(Instant.now());
        }
        if (!orphaned.isEmpty()) {
            runRepository.saveAll(orphaned);
            log.info("Marked {} orphaned load-test run(s) as FAILED after restart", orphaned.size());
        }
    }

    // ─── Start ───────────────────────────────────────────────────────────────────

    public LoadTestResponse startRun(User user, LoadTestRequest request) {
        if (properties.isRequireAuthorizationConfirmation() && !request.authorizedTarget()) {
            throw new BadRequestException("You must confirm that you own, or are explicitly authorized to "
                    + "load test, this target before a run can start. Generating sustained traffic against "
                    + "infrastructure you do not control is indistinguishable from a denial-of-service attack.");
        }

        int active = runRepository.countByOwnerIdAndStatusIn(user.getId(),
                List.of(LoadTestStatus.RUNNING, LoadTestStatus.CONFIGURED));
        if (active >= properties.getMaxConcurrentRunsPerUser()) {
            throw new BadRequestException("You already have " + active + " load test(s) in progress. "
                    + "Wait for them to finish or stop them first — concurrent runs are limited to "
                    + properties.getMaxConcurrentRunsPerUser()
                    + " so a single user cannot exceed the configured traffic ceiling.");
        }

        URI target = validateTarget(request.targetUrl());
        List<String> clampNotes = new ArrayList<>();

        int virtualUsers = clamp("Virtual users", request.virtualUsers(), 1,
                properties.getMaxVirtualUsers(), clampNotes);
        int durationSeconds = clamp("Duration", request.durationSeconds(), 1,
                properties.getMaxDurationSeconds(), clampNotes);
        int rampUpSeconds = clamp("Ramp-up", request.rampUpSeconds(), 0,
                properties.getMaxRampSeconds(), clampNotes);
        int rampDownSeconds = clamp("Ramp-down", request.rampDownSeconds(), 0,
                properties.getMaxRampSeconds(), clampNotes);
        Integer targetRps = request.targetRequestsPerSecond() == null ? null
                : clamp("Target requests/sec", request.targetRequestsPerSecond(), 1,
                        properties.getMaxRequestsPerSecond(), clampNotes);
        int timeoutSeconds = request.requestTimeoutSeconds() == null
                ? properties.getRequestTimeoutSeconds()
                : clamp("Request timeout", request.requestTimeoutSeconds(), 1,
                        properties.getRequestTimeoutSeconds(), clampNotes);

        Map<String, String> headers = sanitizeHeaders(request.headers(), clampNotes);
        String method = request.httpMethod() == null || request.httpMethod().isBlank()
                ? "GET" : request.httpMethod().trim().toUpperCase(Locale.ROOT);

        LoadTestRun run = new LoadTestRun();
        run.setOwner(user);
        run.setProjectId(request.projectId());
        run.setTargetUrl(target.toString());
        run.setHttpMethod(method);
        run.setVirtualUsers(virtualUsers);
        run.setDurationSeconds(durationSeconds);
        run.setRampUpSeconds(rampUpSeconds);
        run.setRampDownSeconds(rampDownSeconds);
        run.setTargetRequestsPerSecond(targetRps);
        run.setRequestTimeoutSeconds(timeoutSeconds);
        run.setRequestBody(request.requestBody());
        run.setRequestHeadersJson(writeJson(headers));
        run.setClampNotes(clampNotes.isEmpty() ? null : truncate(String.join(" ", clampNotes), 990));
        run.setStatus(LoadTestStatus.CONFIGURED);
        run = runRepository.save(run);

        // Committed before the background job starts, so its own connection can see the row. The hand-off
        // goes through AsyncJobLauncher rather than calling a local @Async method, because a self-call
        // would bypass the async proxy and run the whole test inline on this request thread.
        final Long runId = run.getId();
        asyncJobLauncher.launchLoadTestJob("load-test-" + runId, () -> execute(runId));
        return toResponse(run, null);
    }

    // ─── Background execution ────────────────────────────────────────────────────

    /** Generates the traffic. Always invoked on a background thread via {@link AsyncJobLauncher}. */
    public void execute(Long runId) {
        LoadTestRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("Load-test run {} disappeared before execution started", runId);
            return;
        }

        int plannedTotalMs = (run.getRampUpSeconds() + run.getDurationSeconds() + run.getRampDownSeconds()) * 1000;
        LoadTestExecution execution = new LoadTestExecution(runId, plannedTotalMs);
        activeRuns.put(runId, execution);

        run.setStatus(LoadTestStatus.RUNNING);
        run.setStartedAt(Instant.now());
        runRepository.save(run);

        try {
            generateTraffic(run, execution);
            execution.endNanos = System.nanoTime();

            boolean cancelled = execution.cancelled.get();
            flushMetrics(run, execution);
            run.setStatus(cancelled ? LoadTestStatus.CANCELLED : LoadTestStatus.COMPLETED);
            if (cancelled) {
                run.setErrorMessage("Stopped by user after " + (execution.elapsedMs() / 1000)
                        + "s. Metrics below cover only the " + execution.totalRequests.get()
                        + " request(s) actually sent before the stop.");
            }
            run.setCompletedAt(Instant.now());
            runRepository.save(run);

            log.info("Load test {} {}: {} requests, {} ok, {} failed, {} ms elapsed, p95 {} ms",
                    runId, run.getStatus(), run.getTotalRequests(), run.getSuccessfulRequests(),
                    run.getFailedRequests(), run.getActualDurationMs(), run.getP95LatencyMs());

        } catch (Exception ex) {
            log.error("Load test {} failed: {}", runId, ex.getMessage(), ex);
            execution.endNanos = System.nanoTime();
            flushMetrics(run, execution);
            run.setStatus(LoadTestStatus.FAILED);
            run.setErrorMessage(truncate("Load test failed: "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()), 1990));
            run.setCompletedAt(Instant.now());
            runRepository.save(run);
        } finally {
            activeRuns.remove(runId);
        }
    }

    /**
     * Drives the configured virtual users through ramp-up, sustain and ramp-down.
     *
     * <p>Uses one virtual thread per user: these workloads are almost entirely blocked on network I/O,
     * so platform threads would cap useful concurrency at the pool size rather than at the target's
     * capacity — which is the thing being measured.
     */
    private void generateTraffic(LoadTestRun run, LoadTestExecution execution) throws InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Math.min(10, run.getRequestTimeoutSeconds())))
                .build();

        Map<String, String> headers = readHeaders(run.getRequestHeadersJson());
        int virtualUsers = run.getVirtualUsers();
        long rampUpMs = run.getRampUpSeconds() * 1000L;
        long sustainMs = run.getDurationSeconds() * 1000L;
        long rampDownMs = run.getRampDownSeconds() * 1000L;
        long totalMs = rampUpMs + sustainMs + rampDownMs;

        // Per-user pacing derived from the aggregate rate ceiling, so the target is not exceeded
        // in aggregate no matter how fast it responds.
        long perUserDelayMs = 0;
        if (run.getTargetRequestsPerSecond() != null && run.getTargetRequestsPerSecond() > 0) {
            perUserDelayMs = Math.max(1, (1000L * virtualUsers) / run.getTargetRequestsPerSecond());
        }
        final long pacingDelayMs = perUserDelayMs;
        final int maxTotalRequests = properties.getMaxTotalRequests();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(1);
            List<Future<?>> workers = new ArrayList<>(virtualUsers);

            for (int i = 0; i < virtualUsers; i++) {
                final int userIndex = i;
                workers.add(executor.submit(() -> {
                    try {
                        ready.await();
                        // Stagger arrival across the ramp-up window so concurrency climbs gradually
                        // instead of every user hitting the target in the same instant.
                        if (rampUpMs > 0) {
                            long offset = (long) ((userIndex / (double) virtualUsers) * rampUpMs);
                            if (offset > 0 && sleepInterruptibly(offset, execution)) {
                                return;
                            }
                        }
                        // During ramp-down, users retire in the same order they arrived.
                        long retireAtMs = totalMs - (rampDownMs > 0
                                ? (long) (((virtualUsers - userIndex) / (double) virtualUsers) * rampDownMs)
                                : 0);

                        while (!execution.cancelled.get()
                                && execution.elapsedMs() < retireAtMs
                                && execution.totalRequests.get() < maxTotalRequests) {
                            sendOneRequest(httpClient, run, headers, execution);
                            if (pacingDelayMs > 0 && sleepInterruptibly(pacingDelayMs, execution)) {
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            ready.countDown();

            // Bound the wait: if a worker somehow hangs past the plan, the run still terminates rather
            // than leaving a RUNNING row and a leaked executor behind.
            long deadlineMs = totalMs + (run.getRequestTimeoutSeconds() * 1000L) + 15_000;
            long waitStart = System.nanoTime();
            for (Future<?> worker : workers) {
                long remainingMs = deadlineMs - ((System.nanoTime() - waitStart) / 1_000_000L);
                if (remainingMs <= 0) {
                    worker.cancel(true);
                    continue;
                }
                try {
                    worker.get(remainingMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    worker.cancel(true);
                } catch (ExecutionException e) {
                    log.debug("Virtual user ended with an exception: {}", e.getCause() != null
                            ? e.getCause().getMessage() : e.getMessage());
                }
            }
        }
    }

    private void sendOneRequest(HttpClient httpClient, LoadTestRun run, Map<String, String> headers,
                                LoadTestExecution execution) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(run.getTargetUrl()))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(run.getRequestTimeoutSeconds()));
            headers.forEach(builder::header);

            String body = run.getRequestBody();
            HttpRequest.BodyPublisher publisher = (body != null && !body.isBlank())
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            HttpRequest httpRequest = switch (run.getHttpMethod()) {
                case "GET" -> builder.GET().build();
                case "DELETE" -> builder.DELETE().build();
                case "HEAD", "OPTIONS" -> builder.method(run.getHttpMethod(),
                        HttpRequest.BodyPublishers.noBody()).build();
                default -> builder.method(run.getHttpMethod(), publisher).build();
            };

            // Bodies are read and counted (for throughput) but discarded, so response size does not
            // drive the load generator's own memory use.
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            long latencyNanos = System.nanoTime() - startNanos;

            int status = response.statusCode();
            boolean success = status >= 200 && status < 400;
            long bytes = response.body() != null ? response.body().length : 0;
            execution.record(status, latencyNanos, bytes, success);

            captureRateLimitHeaders(response, execution);

        } catch (Exception ex) {
            execution.recordError(ex.getClass().getSimpleName()
                    + (ex.getMessage() != null ? ": " + ex.getMessage() : ""), System.nanoTime() - startNanos);
        }
    }

    private void captureRateLimitHeaders(HttpResponse<?> response, LoadTestExecution execution) {
        response.headers().firstValue("retry-after")
                .ifPresent(value -> {
                    if (execution.retryAfterValues.size() < 10) {
                        execution.retryAfterValues.add(value);
                    }
                });
        // Both the X- prefixed convention and the newer RFC-draft names are checked, since which one a
        // service exposes is entirely implementation-dependent.
        firstHeader(response, "x-ratelimit-limit", "ratelimit-limit")
                .ifPresent(v -> execution.rateLimitLimit.compareAndSet(null, v));
        firstHeader(response, "x-ratelimit-remaining", "ratelimit-remaining")
                .ifPresent(v -> execution.rateLimitRemaining.set(v));
        firstHeader(response, "x-ratelimit-reset", "ratelimit-reset")
                .ifPresent(v -> execution.rateLimitReset.set(v));
    }

    private Optional<String> firstHeader(HttpResponse<?> response, String... names) {
        for (String name : names) {
            Optional<String> value = response.headers().firstValue(name);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /** Sleeps in short slices so a stop request takes effect promptly instead of after a long wait. */
    private boolean sleepInterruptibly(long totalMs, LoadTestExecution execution) throws InterruptedException {
        long remaining = totalMs;
        while (remaining > 0) {
            if (execution.cancelled.get()) {
                return true;
            }
            long slice = Math.min(100, remaining);
            Thread.sleep(slice);
            remaining -= slice;
        }
        return execution.cancelled.get();
    }

    // ─── Metric finalization ─────────────────────────────────────────────────────

    private void flushMetrics(LoadTestRun run, LoadTestExecution execution) {
        List<Long> sorted = execution.sortedLatenciesMs();
        int total = execution.totalRequests.get();
        int successful = execution.successfulRequests.get();
        long elapsedMs = Math.max(1, execution.elapsedMs());

        run.setTotalRequests(total);
        run.setSuccessfulRequests(successful);
        run.setFailedRequests(total - successful);
        run.setErrorRatePercent(total > 0 ? round2((total - successful) * 100.0 / total) : 0);
        // Throughput uses measured elapsed time, not the configured duration: a run stopped early or one
        // whose target was slow would otherwise report a rate it never achieved.
        run.setRequestsPerSecond(round2(total * 1000.0 / elapsedMs));
        run.setActualDurationMs(elapsedMs);
        run.setTotalBytesReceived(execution.totalBytesReceived.get());

        run.setAvgLatencyMs(sorted.isEmpty() ? 0
                : Math.round(sorted.stream().mapToLong(Long::longValue).average().orElse(0)));
        run.setMinLatencyMs(sorted.isEmpty() ? 0 : sorted.get(0));
        run.setMaxLatencyMs(sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1));
        run.setP50LatencyMs(percentile(sorted, 50));
        run.setP90LatencyMs(percentile(sorted, 90));
        run.setP95LatencyMs(percentile(sorted, 95));
        run.setP99LatencyMs(percentile(sorted, 99));

        run.setStatusDistributionJson(writeJson(execution.statusDistribution()));
        run.setTimeSeriesJson(writeJson(buildTimeSeries(execution)));
        run.setRateLimitEvidenceJson(writeJson(buildRateLimitEvidence(execution)));
    }

    private List<LoadTestResponse.TimeSeriesPoint> buildTimeSeries(LoadTestExecution execution) {
        List<LoadTestResponse.TimeSeriesPoint> points = new ArrayList<>();
        int lastSecond = execution.buckets.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int second = 0; second <= lastSecond; second++) {
            LoadTestExecution.SecondBucket bucket = execution.buckets.get(second);
            if (bucket == null) {
                // A second with no completed requests is a real observation (the target was saturated or
                // stalled), so it is emitted as a zero point rather than omitted — dropping it would
                // compress the time axis and hide the gap.
                points.add(new LoadTestResponse.TimeSeriesPoint(second, 0, 0, 0, 0));
                continue;
            }
            List<Long> latencies = new ArrayList<>();
            bucket.latenciesNanos.forEach(nanos -> latencies.add(nanos / 1_000_000L));
            latencies.sort(null);
            long avg = latencies.isEmpty() ? 0
                    : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
            points.add(new LoadTestResponse.TimeSeriesPoint(second, bucket.requests.get(),
                    bucket.errors.get(), avg, percentile(latencies, 95)));
        }
        return points;
    }

    /**
     * Builds the rate-limit verdict strictly from observed evidence. The absence of 429s is reported as
     * "no evidence at this load" rather than "no rate limiting", because a run that stayed under the
     * threshold demonstrates nothing about whether one exists.
     */
    private LoadTestResponse.RateLimitEvidence buildRateLimitEvidence(LoadTestExecution execution) {
        int count429 = execution.http429Count.get();
        int total = execution.totalRequests.get();
        String limitHeader = execution.rateLimitLimit.get();
        boolean detected = count429 > 0 || limitHeader != null;

        String verdict;
        if (count429 > 0) {
            verdict = String.format(Locale.ROOT,
                    "Rate limiting confirmed: %d of %d requests (%.1f%%) were answered with HTTP 429.",
                    count429, total, total > 0 ? count429 * 100.0 / total : 0);
        } else if (limitHeader != null) {
            verdict = "Rate-limit headers are exposed (RateLimit-Limit: " + limitHeader
                    + ") but the threshold was not reached at this load, so no request was throttled.";
        } else {
            verdict = "No rate-limiting evidence observed at this load: no HTTP 429 responses and no "
                    + "RateLimit-* headers. This does not prove the target has no rate limiting — the "
                    + "configured load may simply have stayed below its threshold.";
        }

        return new LoadTestResponse.RateLimitEvidence(detected, verdict, count429,
                new ArrayList<>(new LinkedHashSet<>(execution.retryAfterValues)),
                limitHeader, execution.rateLimitRemaining.get(), execution.rateLimitReset.get());
    }

    // ─── Read / control ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoadTestResponse getRun(User user, Long runId) {
        LoadTestRun run = runRepository.findByIdAndOwnerId(runId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Load test run not found: " + runId));
        return toResponse(run, activeRuns.get(runId));
    }

    @Transactional(readOnly = true)
    public List<LoadTestResponse> listRuns(User user) {
        return runRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId(),
                        PageRequest.of(0, MAX_HISTORY_RESULTS))
                .stream()
                .map(run -> toResponse(run, activeRuns.get(run.getId())))
                .toList();
    }

    /**
     * Signals a running test to stop. Returns immediately; the background job observes the flag between
     * requests, finalizes whatever it measured, and transitions the run to CANCELLED.
     */
    @Transactional(readOnly = true)
    public LoadTestResponse stopRun(User user, Long runId) {
        LoadTestRun run = runRepository.findByIdAndOwnerId(runId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Load test run not found: " + runId));

        LoadTestExecution execution = activeRuns.get(runId);
        if (execution == null) {
            if (run.getStatus() == LoadTestStatus.RUNNING || run.getStatus() == LoadTestStatus.CONFIGURED) {
                throw new BadRequestException("This run is no longer active on this server (it may have finished "
                        + "or the server restarted). Reload to see its final state.");
            }
            throw new BadRequestException("This run has already finished with status " + run.getStatus()
                    + " and cannot be stopped.");
        }
        execution.cancelled.set(true);
        log.info("Stop requested for load-test run {} by user {}", runId, user.getId());
        return toResponse(run, execution);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────────

    /**
     * Maps a run to its response. When {@code live} is present the run is still in flight, so metrics are
     * computed from the in-memory counters — that is what makes the polling UI show real progress rather
     * than an animated guess.
     */
    private LoadTestResponse toResponse(LoadTestRun run, LoadTestExecution live) {
        if (live != null) {
            List<Long> sorted = live.sortedLatenciesMs();
            int total = live.totalRequests.get();
            int successful = live.successfulRequests.get();
            long elapsedMs = Math.max(1, live.elapsedMs());
            return new LoadTestResponse(
                    run.getId(), run.getStatus(), live.progressPercent(), run.getTargetUrl(), run.getHttpMethod(),
                    run.getVirtualUsers(), run.getDurationSeconds(), run.getRampUpSeconds(), run.getRampDownSeconds(),
                    run.getTargetRequestsPerSecond(), run.getRequestTimeoutSeconds(), run.getClampNotes(),
                    total, successful, total - successful,
                    total > 0 ? round2(successful * 100.0 / total) : 0,
                    total > 0 ? round2((total - successful) * 100.0 / total) : 0,
                    round2(total * 1000.0 / elapsedMs),
                    sorted.isEmpty() ? 0 : Math.round(sorted.stream().mapToLong(Long::longValue).average().orElse(0)),
                    sorted.isEmpty() ? 0 : sorted.get(0),
                    sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1),
                    percentile(sorted, 50), percentile(sorted, 90), percentile(sorted, 95), percentile(sorted, 99),
                    elapsedMs, live.totalBytesReceived.get(),
                    live.statusDistribution(), buildTimeSeries(live), buildRateLimitEvidence(live),
                    run.getErrorMessage(), run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt());
        }

        return new LoadTestResponse(
                run.getId(), run.getStatus(),
                run.getStatus() == LoadTestStatus.CONFIGURED ? 0 : 100,
                run.getTargetUrl(), run.getHttpMethod(),
                run.getVirtualUsers(), run.getDurationSeconds(), run.getRampUpSeconds(), run.getRampDownSeconds(),
                run.getTargetRequestsPerSecond(), run.getRequestTimeoutSeconds(), run.getClampNotes(),
                run.getTotalRequests(), run.getSuccessfulRequests(), run.getFailedRequests(),
                run.getTotalRequests() > 0
                        ? round2(run.getSuccessfulRequests() * 100.0 / run.getTotalRequests()) : 0,
                run.getErrorRatePercent(), run.getRequestsPerSecond(),
                run.getAvgLatencyMs(), run.getMinLatencyMs(), run.getMaxLatencyMs(),
                run.getP50LatencyMs(), run.getP90LatencyMs(), run.getP95LatencyMs(), run.getP99LatencyMs(),
                run.getActualDurationMs(), run.getTotalBytesReceived(),
                readStatusDistribution(run.getStatusDistributionJson()),
                readTimeSeries(run.getTimeSeriesJson()),
                readRateLimitEvidence(run.getRateLimitEvidenceJson()),
                run.getErrorMessage(), run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt());
    }

    // ─── Validation helpers ──────────────────────────────────────────────────────

    /**
     * Validates the target URL. Rejects non-HTTP schemes and embedded credentials; deliberately permits
     * loopback and private addresses, because the common legitimate case is a developer testing their own
     * service on localhost or an internal staging host.
     */
    private URI validateTarget(String rawUrl) {
        String candidate = rawUrl.trim();
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new BadRequestException("\"" + rawUrl + "\" is not a valid URL: " + e.getMessage());
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("\"" + rawUrl + "\" has no host component. "
                    + "Provide a full URL such as https://staging.example.com/api/health");
        }
        if (uri.getUserInfo() != null) {
            throw new BadRequestException("Credentials embedded in the URL are not supported. "
                    + "Supply authentication as a request header instead, so it is not logged as part of the URL.");
        }
        return uri;
    }

    private int clamp(String label, int requested, int min, int max, List<String> notes) {
        if (requested < min) {
            notes.add(label + " was raised from " + requested + " to the minimum of " + min + ".");
            return min;
        }
        if (requested > max) {
            notes.add(label + " was reduced from " + requested + " to the configured maximum of " + max + ".");
            return max;
        }
        return requested;
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> requested, List<String> notes) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (requested == null) {
            return sanitized;
        }
        List<String> rejected = new ArrayList<>();
        requested.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            // These are managed by the HTTP client itself; setting them would either be ignored or
            // corrupt the request framing, so they are dropped with an explicit note.
            if (RESTRICTED_HEADERS.contains(normalized) || normalized.equals("user-agent")) {
                rejected.add(name.trim());
                return;
            }
            sanitized.put(name.trim(), value);
        });
        if (!rejected.isEmpty()) {
            notes.add("These headers cannot be overridden and were ignored: " + String.join(", ", rejected) + ".");
        }
        return sanitized;
    }

    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Could not serialize load-test metric payload: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> readHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<Integer, Integer> readStatusDistribution(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<TreeMap<Integer, Integer>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<LoadTestResponse.TimeSeriesPoint> readTimeSeries(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<LoadTestResponse.TimeSeriesPoint>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private LoadTestResponse.RateLimitEvidence readRateLimitEvidence(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LoadTestResponse.RateLimitEvidence.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
