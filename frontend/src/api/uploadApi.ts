import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type { UploadInitResponse, UploadStatusResponse } from '../types/upload';
import type { ProjectResponse } from '../types/project';

export async function initUploadSession(
  fileName: string,
  fileSizeBytes: number,
  projectName?: string,
  description?: string,
): Promise<UploadInitResponse> {
  const { data } = await httpClient.post<ApiResponse<UploadInitResponse>>('/uploads/sessions', {
    fileName,
    fileSizeBytes,
    projectName,
    description,
  });
  return data.data as UploadInitResponse;
}

export async function uploadChunk(sessionId: string, index: number, chunk: Blob): Promise<UploadStatusResponse> {
  const { data } = await httpClient.put<ApiResponse<UploadStatusResponse>>(
    `/uploads/sessions/${sessionId}/chunks/${index}`,
    chunk,
    { headers: { 'Content-Type': 'application/octet-stream' } },
  );
  return data.data as UploadStatusResponse;
}

export async function getUploadStatus(sessionId: string): Promise<UploadStatusResponse> {
  const { data } = await httpClient.get<ApiResponse<UploadStatusResponse>>(`/uploads/sessions/${sessionId}`);
  return data.data as UploadStatusResponse;
}

export async function completeUpload(sessionId: string): Promise<ProjectResponse> {
  const { data } = await httpClient.post<ApiResponse<ProjectResponse>>(`/uploads/sessions/${sessionId}/complete`);
  return data.data as ProjectResponse;
}

export async function abortUpload(sessionId: string): Promise<void> {
  await httpClient.delete(`/uploads/sessions/${sessionId}`);
}
