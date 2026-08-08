import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type { ProjectDetailResponse, ProjectResponse } from '../types/project';

export async function uploadProject(name: string, description: string, file: File): Promise<ProjectResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (name) formData.append('name', name);
  if (description) formData.append('description', description);
  const { data } = await httpClient.post<ApiResponse<ProjectResponse>>('/projects/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data as ProjectResponse;
}

export async function uploadSwaggerSpec(projectId: number, file: File): Promise<ProjectResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await httpClient.post<ApiResponse<ProjectResponse>>(`/projects/${projectId}/swagger`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data as ProjectResponse;
}

export async function listProjects(): Promise<ProjectResponse[]> {
  const { data } = await httpClient.get<ApiResponse<ProjectResponse[]>>('/projects');
  return data.data ?? [];
}

export async function getProjectDetail(projectId: number): Promise<ProjectDetailResponse> {
  const { data } = await httpClient.get<ApiResponse<ProjectDetailResponse>>(`/projects/${projectId}`);
  return data.data as ProjectDetailResponse;
}

export interface CreateProjectPayload {
  /**
   * Optional. When omitted the server derives the name from what discovery actually finds — the
   * OpenAPI document title, the page title, or the hostname — which is more useful than a placeholder.
   */
  name?: string;
  description?: string;
  sourceType: 'ZIP' | 'GIT_URL' | 'OPENAPI' | 'POSTMAN' | 'WEBSITE_URL' | 'API_URL';
  repoUrl?: string;
  targetUrl?: string;
  targetApiUrl?: string;
}

export async function createProjectFromUrl(payload: CreateProjectPayload): Promise<ProjectResponse> {
  const { data } = await httpClient.post<ApiResponse<ProjectResponse>>('/projects/create', payload);
  return data.data as ProjectResponse;
}
