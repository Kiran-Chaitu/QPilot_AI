export type LoadTestStatus = 'CONFIGURED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

/** One second of measured traffic. The source of every load-test chart. */
export interface TimeSeriesPoint {
  secondOffset: number;
  requests: number;
  errors: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
}

/**
 * Observed rate-limit evidence. `detected` is true only when the target actually returned 429s or
 * exposed RateLimit headers — never inferred from their absence.
 */
export interface RateLimitEvidence {
  detected: boolean;
  verdict: string;
  http429Count: number;
  retryAfterValues: string[];
  rateLimitLimitHeader?: string;
  rateLimitRemainingHeader?: string;
  rateLimitResetHeader?: string;
}

export interface LoadTestRun {
  id: number;
  status: LoadTestStatus;
  progressPercent: number;
  targetUrl: string;
  httpMethod: string;
  virtualUsers: number;
  durationSeconds: number;
  rampUpSeconds: number;
  rampDownSeconds: number;
  targetRequestsPerSecond?: number;
  requestTimeoutSeconds: number;
  /** Values the server reduced to fit its safety envelope, so displayed config matches what ran. */
  clampNotes?: string;

  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  successRatePercent: number;
  errorRatePercent: number;
  requestsPerSecond: number;
  avgLatencyMs: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  p50LatencyMs: number;
  p90LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  actualDurationMs: number;
  totalBytesReceived: number;

  statusCodeDistribution: Record<string, number>;
  timeSeries: TimeSeriesPoint[];
  rateLimitEvidence?: RateLimitEvidence;

  errorMessage?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface LoadTestConfig {
  targetUrl: string;
  httpMethod: string;
  virtualUsers: number;
  durationSeconds: number;
  rampUpSeconds: number;
  rampDownSeconds: number;
  targetRequestsPerSecond?: number | null;
  requestTimeoutSeconds?: number | null;
  headers?: Record<string, string>;
  requestBody?: string | null;
  projectId?: number | null;
  /** Required attestation that the caller may test this target. */
  authorizedTarget: boolean;
}
