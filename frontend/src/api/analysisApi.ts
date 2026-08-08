import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type {
  AnalysisResultResponse,
  AnalysisRunResponse,
  GeneratedTestResponse,
  RiskAssessmentResponse,
  SecurityFindingResponse,
  TestExecutionSummary,
} from '../types/analysis';

/**
 * Analysis API client.
 *
 * <p>{@link startAnalysis} returns as soon as the run is queued; the caller polls
 * {@link getLatestAnalysis} and reads `run.progressPercent` / `run.currentStage`. The scan plus optional
 * AI enrichment can take a minute or more, which is far longer than a browser or proxy will hold a
 * request open.
 */

export async function startAnalysis(projectId: number): Promise<AnalysisRunResponse> {
  const { data } = await httpClient.post<ApiResponse<AnalysisRunResponse>>(`/projects/${projectId}/analyze`);
  return data.data as AnalysisRunResponse;
}

export async function getLatestAnalysis(projectId: number): Promise<AnalysisResultResponse> {
  const { data } = await httpClient.get<ApiResponse<AnalysisResultResponse>>(`/projects/${projectId}/analysis`);
  return data.data as AnalysisResultResponse;
}

export async function listGeneratedTests(projectId: number): Promise<GeneratedTestResponse[]> {
  const { data } = await httpClient.get<ApiResponse<GeneratedTestResponse[]>>(`/projects/${projectId}/tests`);
  return data.data ?? [];
}

/**
 * Really executes the project's executable tests against its own configured target and returns the
 * observed outcome counts. The target comes from the project record, never from the caller.
 */
export async function executeProjectTests(projectId: number): Promise<TestExecutionSummary> {
  const { data } = await httpClient.post<ApiResponse<TestExecutionSummary>>(`/projects/${projectId}/tests/execute`, undefined, {
    // Execution issues one bounded HTTP request per test against a live target, so it legitimately
    // takes longer than a normal read.
    timeout: 180_000,
  });
  return data.data as TestExecutionSummary;
}

export async function getSecurityReport(projectId: number): Promise<SecurityFindingResponse[]> {
  const { data } = await httpClient.get<ApiResponse<SecurityFindingResponse[]>>(
    `/projects/${projectId}/security-report`,
  );
  return data.data ?? [];
}

export async function getRiskAssessment(projectId: number): Promise<RiskAssessmentResponse | undefined> {
  const { data } = await httpClient.get<ApiResponse<RiskAssessmentResponse>>(`/projects/${projectId}/risk`);
  return data.data;
}

export type ReportFormat = 'pdf' | 'md' | 'html';

/** Downloads a report. Errors arrive as a Blob, so callers should use `extractBlobErrorMessage`. */
export async function downloadReport(projectId: number, format: ReportFormat): Promise<Blob> {
  const path =
    format === 'pdf'
      ? `/projects/${projectId}/report/download`
      : `/projects/${projectId}/report/download/${format}`;
  const { data } = await httpClient.get(path, { responseType: 'blob', timeout: 90_000 });
  return data as Blob;
}
