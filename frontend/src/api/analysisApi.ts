import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type {
  AnalysisResultResponse,
  GeneratedTestResponse,
  RiskAssessmentResponse,
  SecurityFindingResponse,
} from '../types/analysis';

export async function runAnalysis(projectId: number): Promise<AnalysisResultResponse> {
  const { data } = await httpClient.post<ApiResponse<AnalysisResultResponse>>(`/projects/${projectId}/analyze`);
  return data.data as AnalysisResultResponse;
}

export async function getLatestAnalysis(projectId: number): Promise<AnalysisResultResponse> {
  const { data } = await httpClient.get<ApiResponse<AnalysisResultResponse>>(`/projects/${projectId}/analysis`);
  return data.data as AnalysisResultResponse;
}

export async function listGeneratedTests(projectId: number): Promise<GeneratedTestResponse[]> {
  const { data } = await httpClient.get<ApiResponse<GeneratedTestResponse[]>>(`/projects/${projectId}/tests`);
  return data.data ?? [];
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

export async function downloadReport(projectId: number): Promise<Blob> {
  const { data } = await httpClient.get(`/projects/${projectId}/report/download`, {
    responseType: 'blob',
  });
  return data as Blob;
}
