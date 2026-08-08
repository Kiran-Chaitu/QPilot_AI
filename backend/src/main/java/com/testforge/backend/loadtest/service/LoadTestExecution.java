package com.testforge.backend.loadtest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live, in-memory state of one running load test.
 *
 * <p>Kept separate from the JPA entity on purpose. Metrics are written by hundreds of concurrent
 * virtual-user threads thousands of times per run; funnelling that through the database would make the
 * measurement infrastructure itself the bottleneck and distort the very latencies being measured. So
 * counters live here in lock-free atomics during the run, are read directly to answer progress polls,
 * and are flushed to the entity once at the end.
 *
 * <p>Latencies are recorded in nanoseconds from {@code System.nanoTime()} and only converted to
 * milliseconds at the end, so sub-millisecond responses to a local target are not all rounded to zero.
 */
class LoadTestExecution {

    final long runId;

    /** Flipped by the stop endpoint; every virtual user checks it between requests. */
    final AtomicBoolean cancelled = new AtomicBoolean(false);

    final AtomicInteger totalRequests = new AtomicInteger();
    final AtomicInteger successfulRequests = new AtomicInteger();
    final AtomicLong totalBytesReceived = new AtomicLong();
    final AtomicInteger http429Count = new AtomicInteger();

    /** Every completed request's latency in nanoseconds — the sample percentiles are computed from. */
    final ConcurrentLinkedQueue<Long> latenciesNanos = new ConcurrentLinkedQueue<>();

    final ConcurrentHashMap<Integer, AtomicInteger> statusCodes = new ConcurrentHashMap<>();

    /** Per-second buckets, keyed by whole seconds elapsed since the run began. */
    final ConcurrentHashMap<Integer, SecondBucket> buckets = new ConcurrentHashMap<>();

    final ConcurrentLinkedQueue<String> retryAfterValues = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> rateLimitLimit = new AtomicReference<>();
    final AtomicReference<String> rateLimitRemaining = new AtomicReference<>();
    final AtomicReference<String> rateLimitReset = new AtomicReference<>();

    /** Transport-level failure messages, sampled for diagnostics rather than accumulated unboundedly. */
    final ConcurrentLinkedQueue<String> errorSamples = new ConcurrentLinkedQueue<>();

    final long startNanos;
    final int plannedTotalMs;
    volatile long endNanos;

    LoadTestExecution(long runId, int plannedTotalMs) {
        this.runId = runId;
        this.plannedTotalMs = plannedTotalMs;
        this.startNanos = System.nanoTime();
    }

    /** One second of measurements, accumulated concurrently by many virtual users. */
    static class SecondBucket {
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        final ConcurrentLinkedQueue<Long> latenciesNanos = new ConcurrentLinkedQueue<>();
    }

    void record(int statusCode, long latencyNanos, long bytes, boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successfulRequests.incrementAndGet();
        }
        totalBytesReceived.addAndGet(bytes);
        latenciesNanos.add(latencyNanos);
        statusCodes.computeIfAbsent(statusCode, k -> new AtomicInteger()).incrementAndGet();
        if (statusCode == 429) {
            http429Count.incrementAndGet();
        }

        SecondBucket bucket = buckets.computeIfAbsent(elapsedSeconds(), k -> new SecondBucket());
        bucket.requests.incrementAndGet();
        bucket.latenciesNanos.add(latencyNanos);
        if (!success) {
            bucket.errors.incrementAndGet();
        }
    }

    void recordError(String message, long latencyNanos) {
        // Status code 0 is the conventional marker for "no HTTP response at all" (DNS failure,
        // connection refused, timeout). Counting these as requests keeps the error rate honest.
        record(0, latencyNanos, 0, false);
        if (errorSamples.size() < 20) {
            errorSamples.add(message);
        }
    }

    int elapsedSeconds() {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000_000L);
    }

    long elapsedMs() {
        long end = endNanos != 0 ? endNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000L;
    }

    int progressPercent() {
        if (plannedTotalMs <= 0) {
            return 100;
        }
        return (int) Math.min(99, elapsedMs() * 100 / plannedTotalMs);
    }

    List<Long> sortedLatenciesMs() {
        List<Long> millis = new ArrayList<>(latenciesNanos.size());
        for (Long nanos : latenciesNanos) {
            millis.add(nanos / 1_000_000L);
        }
        millis.sort(null);
        return millis;
    }

    Map<Integer, Integer> statusDistribution() {
        Map<Integer, Integer> distribution = new java.util.TreeMap<>();
        statusCodes.forEach((code, count) -> distribution.put(code, count.get()));
        return distribution;
    }
}
