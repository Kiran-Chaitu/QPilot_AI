package com.testforge.backend.loadtest.entity;

import com.testforge.backend.auth.entity.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A persisted record of one load test: its configuration and the metrics actually measured.
 *
 * <p>Runs are stored rather than returned-and-forgotten for two reasons. Historical results are what
 * make the dashboard's performance trend real instead of decorative, and a completed run remains
 * auditable — you can see later exactly what was fired at which target, when, and by whom.
 *
 * <p>Latency figures are computed from per-request measurements taken with {@code System.nanoTime()}
 * during the run; the percentiles are derived from the full sorted sample, not from an estimate.
 */
@Entity
@Table(name = "load_test_runs", indexes = {
        @Index(name = "idx_load_test_owner", columnList = "owner_id"),
        @Index(name = "idx_load_test_status", columnList = "status")
})
public class LoadTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Optional project association, so a run can be attributed to a workspace project. */
    private Long projectId;

    // ─── Configuration as requested (post-clamping to the safety envelope) ───────

    @Column(nullable = false, length = 2000)
    private String targetUrl;

    @Column(nullable = false, length = 10)
    private String httpMethod;

    private int virtualUsers;
    private int durationSeconds;
    private int rampUpSeconds;
    private int rampDownSeconds;
    private Integer targetRequestsPerSecond;
    private int requestTimeoutSeconds;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String requestHeadersJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String requestBody;

    /**
     * Whether any configured value had to be reduced to fit the server-side safety envelope, and which.
     * Surfaced in the UI so a run that was clamped never silently reports different settings than
     * the ones it actually ran with.
     */
    @Column(length = 1000)
    private String clampNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadTestStatus status = LoadTestStatus.CONFIGURED;

    // ─── Measured results ────────────────────────────────────────────────────────

    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private double errorRatePercent;
    private double requestsPerSecond;
    private long avgLatencyMs;
    private long minLatencyMs;
    private long maxLatencyMs;
    private long p50LatencyMs;
    private long p90LatencyMs;
    private long p95LatencyMs;
    private long p99LatencyMs;
    private long actualDurationMs;
    private long totalBytesReceived;

    /** JSON object of {statusCode: count}, including 0 for transport failures. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String statusDistributionJson;

    /** JSON array of per-second measurements, used to draw the run's real time-series charts. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String timeSeriesJson;

    /** JSON object of observed rate-limit evidence (429 count, Retry-After values, RateLimit-* headers). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String rateLimitEvidenceJson;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant completedAt;

    public LoadTestRun() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public int getVirtualUsers() { return virtualUsers; }
    public void setVirtualUsers(int virtualUsers) { this.virtualUsers = virtualUsers; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getRampUpSeconds() { return rampUpSeconds; }
    public void setRampUpSeconds(int rampUpSeconds) { this.rampUpSeconds = rampUpSeconds; }

    public int getRampDownSeconds() { return rampDownSeconds; }
    public void setRampDownSeconds(int rampDownSeconds) { this.rampDownSeconds = rampDownSeconds; }

    public Integer getTargetRequestsPerSecond() { return targetRequestsPerSecond; }
    public void setTargetRequestsPerSecond(Integer targetRequestsPerSecond) { this.targetRequestsPerSecond = targetRequestsPerSecond; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }

    public String getRequestHeadersJson() { return requestHeadersJson; }
    public void setRequestHeadersJson(String requestHeadersJson) { this.requestHeadersJson = requestHeadersJson; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public String getClampNotes() { return clampNotes; }
    public void setClampNotes(String clampNotes) { this.clampNotes = clampNotes; }

    public LoadTestStatus getStatus() { return status; }
    public void setStatus(LoadTestStatus status) { this.status = status; }

    public int getTotalRequests() { return totalRequests; }
    public void setTotalRequests(int totalRequests) { this.totalRequests = totalRequests; }

    public int getSuccessfulRequests() { return successfulRequests; }
    public void setSuccessfulRequests(int successfulRequests) { this.successfulRequests = successfulRequests; }

    public int getFailedRequests() { return failedRequests; }
    public void setFailedRequests(int failedRequests) { this.failedRequests = failedRequests; }

    public double getErrorRatePercent() { return errorRatePercent; }
    public void setErrorRatePercent(double errorRatePercent) { this.errorRatePercent = errorRatePercent; }

    public double getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(double requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }

    public long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public long getMinLatencyMs() { return minLatencyMs; }
    public void setMinLatencyMs(long minLatencyMs) { this.minLatencyMs = minLatencyMs; }

    public long getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }

    public long getP50LatencyMs() { return p50LatencyMs; }
    public void setP50LatencyMs(long p50LatencyMs) { this.p50LatencyMs = p50LatencyMs; }

    public long getP90LatencyMs() { return p90LatencyMs; }
    public void setP90LatencyMs(long p90LatencyMs) { this.p90LatencyMs = p90LatencyMs; }

    public long getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }

    public long getP99LatencyMs() { return p99LatencyMs; }
    public void setP99LatencyMs(long p99LatencyMs) { this.p99LatencyMs = p99LatencyMs; }

    public long getActualDurationMs() { return actualDurationMs; }
    public void setActualDurationMs(long actualDurationMs) { this.actualDurationMs = actualDurationMs; }

    public long getTotalBytesReceived() { return totalBytesReceived; }
    public void setTotalBytesReceived(long totalBytesReceived) { this.totalBytesReceived = totalBytesReceived; }

    public String getStatusDistributionJson() { return statusDistributionJson; }
    public void setStatusDistributionJson(String statusDistributionJson) { this.statusDistributionJson = statusDistributionJson; }

    public String getTimeSeriesJson() { return timeSeriesJson; }
    public void setTimeSeriesJson(String timeSeriesJson) { this.timeSeriesJson = timeSeriesJson; }

    public String getRateLimitEvidenceJson() { return rateLimitEvidenceJson; }
    public void setRateLimitEvidenceJson(String rateLimitEvidenceJson) { this.rateLimitEvidenceJson = rateLimitEvidenceJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
