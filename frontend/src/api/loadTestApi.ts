import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type { LoadTestConfig, LoadTestRun } from '../types/loadtest';

/**
 * Load-test API client.
 *
 * <p>Starting a run is asynchronous by design: {@link startLoadTest} returns as soon as the server has
 * accepted the configuration, and the caller polls {@link getLoadTestRun} for live partial metrics. That
 * is what makes a two-minute test possible without holding an HTTP request open for two minutes.
 */

export async function startLoadTest(config: LoadTestConfig): Promise<LoadTestRun> {
  const { data } = await httpClient.post<ApiResponse<LoadTestRun>>('/loadtest/runs', config);
  return data.data as LoadTestRun;
}

export async function getLoadTestRun(runId: number): Promise<LoadTestRun> {
  const { data } = await httpClient.get<ApiResponse<LoadTestRun>>(`/loadtest/runs/${runId}`);
  return data.data as LoadTestRun;
}

/**
 * Requests that a running test stop. Returns immediately — the run finalizes with the metrics it had
 * already measured and transitions to CANCELLED, so partial results are kept rather than discarded.
 */
export async function stopLoadTest(runId: number): Promise<LoadTestRun> {
  const { data } = await httpClient.post<ApiResponse<LoadTestRun>>(`/loadtest/runs/${runId}/stop`);
  return data.data as LoadTestRun;
}

export async function listLoadTestRuns(): Promise<LoadTestRun[]> {
  const { data } = await httpClient.get<ApiResponse<LoadTestRun[]>>('/loadtest/runs');
  return data.data ?? [];
}
