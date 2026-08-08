package com.testforge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Safety envelope for the load-test engine.
 *
 * <p>This exists because the feature generates real traffic at a real host. Every limit here is a
 * deliberate ceiling on how much damage a single request to QPilot can do, and each is enforced
 * server-side — the UI's sliders are a convenience, not the control. Operators can tighten these for
 * a shared deployment without touching code.
 */
@ConfigurationProperties(prefix = "app.loadtest")
public class LoadTestProperties {

    /** Hard ceiling on concurrent virtual users, whatever the client asks for. */
    private int maxVirtualUsers = 200;

    /** Hard ceiling on the sustain phase, in seconds. */
    private int maxDurationSeconds = 120;

    /** Hard ceiling on ramp-up and ramp-down phases, in seconds. */
    private int maxRampSeconds = 60;

    /**
     * Absolute cap on requests issued by a single run. Binds total volume independently of
     * users x duration, so no combination of the other limits can produce an unbounded run.
     */
    private int maxTotalRequests = 20_000;

    /** Ceiling on the aggregate request rate a run may target. */
    private int maxRequestsPerSecond = 500;

    /** Per-request timeout, in seconds. */
    private int requestTimeoutSeconds = 15;

    /**
     * Concurrent runs allowed per user. Defaults to 1 so a user cannot multiply their own throughput
     * ceiling by launching several runs at the same target simultaneously.
     */
    private int maxConcurrentRunsPerUser = 1;

    /**
     * When true, a run is rejected unless the caller explicitly confirms they are authorized to test
     * the target. Load testing infrastructure you do not own is indistinguishable from a denial of
     * service attack, so the confirmation is required by default rather than opt-in.
     */
    private boolean requireAuthorizationConfirmation = true;

    public int getMaxVirtualUsers() { return maxVirtualUsers; }
    public void setMaxVirtualUsers(int maxVirtualUsers) { this.maxVirtualUsers = maxVirtualUsers; }

    public int getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(int maxDurationSeconds) { this.maxDurationSeconds = maxDurationSeconds; }

    public int getMaxRampSeconds() { return maxRampSeconds; }
    public void setMaxRampSeconds(int maxRampSeconds) { this.maxRampSeconds = maxRampSeconds; }

    public int getMaxTotalRequests() { return maxTotalRequests; }
    public void setMaxTotalRequests(int maxTotalRequests) { this.maxTotalRequests = maxTotalRequests; }

    public int getMaxRequestsPerSecond() { return maxRequestsPerSecond; }
    public void setMaxRequestsPerSecond(int maxRequestsPerSecond) { this.maxRequestsPerSecond = maxRequestsPerSecond; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }

    public int getMaxConcurrentRunsPerUser() { return maxConcurrentRunsPerUser; }
    public void setMaxConcurrentRunsPerUser(int maxConcurrentRunsPerUser) { this.maxConcurrentRunsPerUser = maxConcurrentRunsPerUser; }

    public boolean isRequireAuthorizationConfirmation() { return requireAuthorizationConfirmation; }
    public void setRequireAuthorizationConfirmation(boolean requireAuthorizationConfirmation) {
        this.requireAuthorizationConfirmation = requireAuthorizationConfirmation;
    }
}
