export type UploadSessionStatus = 'INITIATED' | 'UPLOADING' | 'ASSEMBLING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';

export interface UploadInitResponse {
  sessionId: string;
  chunkSizeBytes: number;
  totalChunks: number;
  expiresAt: string;
}

export interface UploadStatusResponse {
  sessionId: string;
  status: UploadSessionStatus;
  totalChunks: number;
  receivedChunks: number[];
  resultProjectId?: number;
  errorMessage?: string;
}
