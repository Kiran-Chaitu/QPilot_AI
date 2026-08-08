import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

export interface E2eTestResult {
  checkName: string;
  category: string;
  passed: boolean;
  httpStatus: number;
  responseTimeMs: number;
  details: string | null;
  errorMessage: string | null;
}

export interface E2eTestResponse {
  targetUrl: string;
  totalChecks: number;
  passedChecks: number;
  failedChecks: number;
  testResults: E2eTestResult[];
  generatedPlaywrightScript: string;
  executionTimeMs: number;
}

export async function runE2eTest(
  targetUrl: string,
  loginUrl?: string,
  username?: string,
  password?: string,
  testScenarios?: string[]
): Promise<E2eTestResponse> {
  const { data } = await httpClient.post<ApiResponse<E2eTestResponse>>('/e2e-test/run', {
    targetUrl,
    loginUrl: loginUrl || null,
    username: username || null,
    password: password || null,
    testScenarios: testScenarios || [],
  });
  return data.data as E2eTestResponse;
}
