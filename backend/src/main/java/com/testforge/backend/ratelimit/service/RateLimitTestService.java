package com.testforge.backend.ratelimit.service;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.config.LoadTestProperties;
import com.testforge.backend.ratelimit.dto.RateLimitTestRequest;
import com.testforge.backend.ratelimit.dto.RateLimitTestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probes whether a target actually enforces rate limiting.
 *
 * <p>Two phases, because they detect different mechanisms: a fast burst trips token-bucket and
 * burst-capacity limiters, while a paced sustained phase trips fixed/sliding-window limiters that a short
 * burst can slip under. A target may enforce one and not the other, so reporting only one would produce a
 * confident wrong answer.
 *
 * <p>The design rule throughout is that absence of evidence is reported as absence of evidence. If no
 * request is throttled, the verdict says no limiting was observed <em>at the applied load</em> and states
 * what load that was — it never asserts the target is unprotected, which the probe cannot establish.
 *
 * <p>Traffic is bounded by the same operator-configured envelope as load testing, and the caller must
 * attest authorization: a burst probe is deliberately abusive traffic and is only legitimate against
 * infrastructure you control.
 */
@Service
public class RateLimitTestService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitTestService.class);

    private static final String USER_AGENT = "QPilot-AI-RateLimitProbe/1.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    /** Header names that indicate rate-limit policy, checked across the common conventions. */
    private static final List<String> RATE_LIMIT_HEADER_NAMES = List.of(
            "x-ratelimit-limit", "x-ratelimit-remaining", "x-ratelimit-reset",
            "ratelimit-limit", "ratelimit-remaining", "ratelimit-reset", "ratelimit-policy",
            "x-rate-limit-limit", "x-rate-limit-remaining", "x-rate-limit-reset",
            "retry-after");

    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host", "connection", "content-length", "upgrade", "transfer-encoding", "user-agent");

    private final LoadTestProperties properties;
    private final HttpClient httpClient;

    public RateLimitTestService(LoadTestProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public RateLimitTestResponse probe(RateLimitTestRequest request) {
        if (properties.isRequireAuthorizationConfirmation() && !request.authorizedTarget()) {
            throw new BadRequestException("You must confirm that you own, or are explicitly authorized to test, "
                    + "this target. A rate-limit probe deliberately sends abusive burst traffic and is only "
                    + "legitimate against infrastructure you control.");
        }

        URI target = validateTarget(request.targetUrl());
        String method = request.httpMethod() == null || request.httpMethod().isBlank()
                ? "GET" : request.httpMethod().trim().toUpperCase(Locale.ROOT);
        Map<String, String> headers = sanitizeHeaders(request.headers());

        int burstRequests = Math.min(request.burstRequests(), properties.getMaxRequestsPerSecond());
        int sustainedRequests = Math.min(request.sustainedRequests(), properties.getMaxTotalRequests());
        int sustainedRps = Math.min(request.sustainedRequestsPerSecond(), properties.getMaxRequestsPerSecond());

        List<String> notes = new ArrayList<>();
        if (burstRequests < request.burstRequests()) {
            notes.add("Burst size was reduced to " + burstRequests + " to stay within the configured limit.");
        }
        if (sustainedRequests < request.sustainedRequests()) {
            notes.add("Sustained request count was reduced to " + sustainedRequests + ".");
        }

        Observations observations = new Observations();
        long startNanos = System.nanoTime();

        RateLimitTestResponse.PhaseResult burst = runBurstPhase(target, method, headers,
                request.requestBody(), burstRequests, observations);

        RateLimitTestResponse.PhaseResult sustained = null;
        if (sustainedRequests > 0) {
            sustained = runSustainedPhase(target, method, headers, request.requestBody(),
                    sustainedRequests, sustainedRps, observations);
        } else {
            notes.add("Sustained phase was skipped because the requested sustained request count was 0. "
                    + "Window-based limiters that a short burst does not trip would not be detected.");
        }

        long totalDurationMs = (System.nanoTime() - startNanos) / 1_000_000;

        int total429 = burst.throttled429Count() + (sustained != null ? sustained.throttled429Count() : 0);
        boolean headersAdvertiseLimit = observations.rateLimitLimit != null
                || !observations.observedHeaderNames.isEmpty();
        boolean detected = total429 > 0 || observations.rateLimitLimit != null;

        String verdict = buildVerdict(total429, burst, sustained, observations, burstRequests, sustainedRps);
        if (!detected && headersAdvertiseLimit) {
            notes.add("Rate-limit-related headers were present but no explicit limit value was advertised.");
        }

        log.info("Rate-limit probe of {} {}: burst {} req ({} throttled), sustained {} req ({} throttled)",
                method, target, burst.requestsSent(), burst.throttled429Count(),
                sustained != null ? sustained.requestsSent() : 0,
                sustained != null ? sustained.throttled429Count() : 0);

        return new RateLimitTestResponse(target.toString(), method, detected, verdict, burst, sustained,
                observations.toEvidence(), notes, totalDurationMs, Instant.now());
    }

    // ─── Phases ──────────────────────────────────────────────────────────────────

    /**
     * Fires every burst request concurrently. Sequence numbers are assigned before dispatch so
     * {@code firstThrottledAtRequest} reflects issue order — the closest observable proxy for where the
     * limiter's threshold sits.
     */
    private RateLimitTestResponse.PhaseResult runBurstPhase(URI target, String method,
                                                           Map<String, String> headers, String body,
                                                           int count, Observations observations) {
        long startNanos = System.nanoTime();
        List<RequestOutcome> outcomes = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RequestOutcome>> futures = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                final int sequence = i;
                futures.add(executor.submit(() -> sendOne(target, method, headers, body, sequence, observations)));
            }
            for (Future<RequestOutcome> future : futures) {
                try {
                    outcomes.add(future.get(REQUEST_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS));
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        return summarize("BURST", outcomes, (System.nanoTime() - startNanos) / 1_000_000);
    }

    /** Paced phase: requests spread evenly to hold a steady rate a window-based limiter can act on. */
    private RateLimitTestResponse.PhaseResult runSustainedPhase(URI target, String method,
                                                               Map<String, String> headers, String body,
                                                               int count, int requestsPerSecond,
                                                               Observations observations) {
        long startNanos = System.nanoTime();
        long intervalMs = Math.max(1, 1000L / requestsPerSecond);
        List<RequestOutcome> outcomes = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            outcomes.add(sendOne(target, method, headers, body, i, observations));
            if (i < count) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return summarize("SUSTAINED", outcomes, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private record RequestOutcome(int sequence, int statusCode, long latencyMs, String error) {
        boolean throttled() {
            return statusCode == 429;
        }

        boolean success() {
            return statusCode >= 200 && statusCode < 400;
        }
    }

    private RequestOutcome sendOne(URI target, String method, Map<String, String> headers, String body,
                                   int sequence, Observations observations) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(target)
                    .header("User-Agent", USER_AGENT)
                    .timeout(REQUEST_TIMEOUT);
            headers.forEach(builder::header);

            HttpRequest.BodyPublisher publisher = (body != null && !body.isBlank())
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            HttpRequest httpRequest = switch (method) {
                case "GET" -> builder.GET().build();
                case "DELETE" -> builder.DELETE().build();
                case "HEAD", "OPTIONS" -> builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
                default -> builder.method(method, publisher).build();
            };

            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            observations.capture(response);
            return new RequestOutcome(sequence, response.statusCode(), latencyMs, null);

        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            // Status 0 marks "no HTTP response received", keeping transport failures distinguishable
            // from an actual 429 rather than silently inflating the throttle count.
            return new RequestOutcome(sequence, 0, latencyMs,
                    e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    private RateLimitTestResponse.PhaseResult summarize(String phase, List<RequestOutcome> outcomes, long durationMs) {
        Map<Integer, Integer> distribution = new TreeMap<>();
        int throttled = 0;
        int success = 0;
        int otherErrors = 0;
        long latencySum = 0;
        Integer firstThrottledAt = null;

        List<RequestOutcome> ordered = new ArrayList<>(outcomes);
        ordered.sort(Comparator.comparingInt(RequestOutcome::sequence));

        for (RequestOutcome outcome : ordered) {
            distribution.merge(outcome.statusCode(), 1, Integer::sum);
            latencySum += outcome.latencyMs();
            if (outcome.throttled()) {
                throttled++;
                if (firstThrottledAt == null) {
                    firstThrottledAt = outcome.sequence();
                }
            } else if (outcome.success()) {
                success++;
            } else {
                otherErrors++;
            }
        }

        int sent = ordered.size();
        double observedRps = durationMs > 0 ? Math.round(sent * 1000.0 / durationMs * 100.0) / 100.0 : 0;
        long avgLatency = sent > 0 ? latencySum / sent : 0;

        return new RateLimitTestResponse.PhaseResult(phase, sent, success, throttled, otherErrors,
                firstThrottledAt, observedRps, avgLatency, durationMs, distribution);
    }

    // ─── Verdict ─────────────────────────────────────────────────────────────────

    private String buildVerdict(int total429, RateLimitTestResponse.PhaseResult burst,
                                RateLimitTestResponse.PhaseResult sustained, Observations observations,
                                int burstRequests, int sustainedRps) {
        if (total429 > 0) {
            StringBuilder sb = new StringBuilder("Rate limiting confirmed by observation: ");
            sb.append(total429).append(" request(s) were answered with HTTP 429. ");
            if (burst.firstThrottledAtRequest() != null) {
                sb.append("In the burst phase the first throttled response arrived at request #")
                        .append(burst.firstThrottledAtRequest()).append(" of ").append(burst.requestsSent())
                        .append(" (observed ").append(burst.observedRequestsPerSec()).append(" req/s). ");
            }
            if (sustained != null && sustained.firstThrottledAtRequest() != null) {
                sb.append("The sustained phase at ~").append(sustainedRps)
                        .append(" req/s was throttled from request #")
                        .append(sustained.firstThrottledAtRequest()).append(". ");
            }
            if (!observations.retryAfterValues.isEmpty()) {
                sb.append("Retry-After was supplied (")
                        .append(String.join(", ", observations.retryAfterValues))
                        .append("), so clients are told when to retry. ");
            } else {
                sb.append("No Retry-After header was supplied, so clients have no guidance on when to retry — "
                        + "consider adding one. ");
            }
            return sb.toString().trim();
        }

        if (observations.rateLimitLimit != null) {
            return "Rate-limit policy is advertised (RateLimit-Limit: " + observations.rateLimitLimit
                    + (observations.rateLimitRemaining != null
                        ? ", Remaining: " + observations.rateLimitRemaining : "")
                    + ") but no request was throttled at the load applied (" + burstRequests
                    + "-request burst"
                    + (sustained != null ? " plus ~" + sustainedRps + " req/s sustained" : "")
                    + "). The limiter exists; this probe stayed under its threshold.";
        }

        return "No rate-limiting evidence was observed: no HTTP 429 responses and no RateLimit-* headers "
                + "across a " + burstRequests + "-request burst"
                + (sustained != null ? " and " + sustained.requestsSent() + " sustained requests at ~"
                    + sustainedRps + " req/s" : "")
                + ". This is not proof that the target has no rate limiting — the applied load may simply "
                + "have stayed below its threshold, or limiting may be enforced further upstream. To claim "
                + "an endpoint is unprotected you would need to probe well above its expected peak traffic.";
    }

    // ─── Observation accumulator ─────────────────────────────────────────────────

    /** Collects rate-limit signals seen across every response in both phases. */
    private static class Observations {
        private volatile String rateLimitLimit;
        private volatile String rateLimitRemaining;
        private volatile String rateLimitReset;
        private final List<String> retryAfterValues = Collections.synchronizedList(new ArrayList<>());
        private final Set<String> observedHeaderNames = ConcurrentHashMap.newKeySet();
        private final AtomicInteger retryAfterCount = new AtomicInteger();

        void capture(HttpResponse<?> response) {
            for (String name : RATE_LIMIT_HEADER_NAMES) {
                response.headers().firstValue(name).ifPresent(value -> {
                    observedHeaderNames.add(name);
                    switch (name) {
                        case "x-ratelimit-limit", "ratelimit-limit", "x-rate-limit-limit" -> rateLimitLimit = value;
                        case "x-ratelimit-remaining", "ratelimit-remaining", "x-rate-limit-remaining" ->
                                rateLimitRemaining = value;
                        case "x-ratelimit-reset", "ratelimit-reset", "x-rate-limit-reset" -> rateLimitReset = value;
                        case "retry-after" -> {
                            retryAfterCount.incrementAndGet();
                            synchronized (retryAfterValues) {
                                if (!retryAfterValues.contains(value) && retryAfterValues.size() < 10) {
                                    retryAfterValues.add(value);
                                }
                            }
                        }
                        default -> { /* recorded in observedHeaderNames only */ }
                    }
                });
            }
        }

        RateLimitTestResponse.Evidence toEvidence() {
            return new RateLimitTestResponse.Evidence(rateLimitLimit, rateLimitRemaining, rateLimitReset,
                    new ArrayList<>(retryAfterValues), retryAfterCount.get() > 0,
                    new ArrayList<>(observedHeaderNames));
        }
    }

    // ─── Validation ──────────────────────────────────────────────────────────────

    private URI validateTarget(String rawUrl) {
        String candidate = rawUrl.trim();
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("\"" + rawUrl + "\" has no host component. Provide a full URL.");
            }
            if (uri.getUserInfo() != null) {
                throw new BadRequestException("Credentials embedded in the URL are not supported. "
                        + "Supply authentication as a request header instead.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new BadRequestException("\"" + rawUrl + "\" is not a valid URL: " + e.getMessage());
        }
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> requested) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (requested == null) {
            return sanitized;
        }
        requested.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            if (!RESTRICTED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT))) {
                sanitized.put(name.trim(), value);
            }
        });
        return sanitized;
    }
}
