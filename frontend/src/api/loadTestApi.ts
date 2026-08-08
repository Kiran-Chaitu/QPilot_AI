import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

export interface RateLimitPolicyItem {
  policy: string;
  value: string;
  status: string;
}

export interface LoadTestResponse {
  targetUrl: string;
  vus: number;
  durationSeconds: number;
  rampUpSeconds: number;
  rpsThroughput: number;
  avgLatencyMs: number;
  p50Ms: number;
  p90Ms: number;
  p95Ms: number;
  p99Ms: number;
  successRatePercent: number;
  errorRatePercent: number;
  rateLimitStatus: string;
  rateLimitPolicies: RateLimitPolicyItem[];
  k6Script: string;
  jmeterScript: string;
}

export async function runLoadTest(
  targetUrl: string,
  vus: number,
  durationSeconds: number,
  rampUpSeconds: number
): Promise<LoadTestResponse> {
  const { data } = await httpClient.post<ApiResponse<LoadTestResponse>>('/loadtest/run', {
    targetUrl,
    vus,
    durationSeconds,
    rampUpSeconds,
  });
  return data.data as LoadTestResponse;
}
