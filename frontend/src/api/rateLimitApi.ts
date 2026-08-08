import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

export interface RateLimitPhaseResult {
  phase: string;
  requestsSent: number;
  successCount: number;
  throttled429Count: number;
  otherErrorCount: number;
  /** 1-based index of the first request answered with 429 — the closest observable threshold. */
  firstThrottledAtRequest?: number;
  observedRequestsPerSec: number;
  avgLatencyMs: number;
  durationMs: number;
  statusDistribution: Record<string, number>;
}

/** Raw header signals. A null field means the target did not advertise that header. */
export interface RateLimitEvidenceDetail {
  rateLimitLimit?: string;
  rateLimitRemaining?: string;
  rateLimitReset?: string;
  retryAfterValues: string[];
  retryAfterHonoured: boolean;
  allRateLimitHeaderNames: string[];
}

export interface RateLimitTestResult {
  targetUrl: string;
  httpMethod: string;
  /** True only when a request was actually throttled or a limit header was advertised. */
  rateLimitingDetected: boolean;
  verdict: string;
  burst: RateLimitPhaseResult;
  sustained?: RateLimitPhaseResult;
  evidence: RateLimitEvidenceDetail;
  notes: string[];
  totalDurationMs: number;
  executedAt: string;
}

export interface RateLimitTestConfig {
  targetUrl: string;
  httpMethod: string;
  burstRequests: number;
  sustainedRequests: number;
  sustainedRequestsPerSecond: number;
  headers?: Record<string, string>;
  requestBody?: string | null;
  /** Required attestation — a burst probe is deliberately abusive traffic. */
  authorizedTarget: boolean;
}

export async function probeRateLimit(config: RateLimitTestConfig): Promise<RateLimitTestResult> {
  const { data } = await httpClient.post<ApiResponse<RateLimitTestResult>>('/ratelimit/probe', config);
  return data.data as RateLimitTestResult;
}
