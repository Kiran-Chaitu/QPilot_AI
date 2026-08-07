import { abortUpload, completeUpload, getUploadStatus, initUploadSession, uploadChunk } from './uploadApi';
import type { ProjectResponse } from '../types/project';

export interface ChunkUploadProgress {
  sentChunks: number;
  totalChunks: number;
  percent: number;
}

const MAX_RETRIES_PER_CHUNK = 3;
const RETRY_BASE_DELAY_MS = 500;

export class UploadCancelledError extends Error {
  constructor() {
    super('Upload cancelled');
    this.name = 'UploadCancelledError';
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Uploads a large file as many small chunks instead of one giant request, so:
 *  - progress can be reported chunk-by-chunk instead of an opaque spinner
 *  - a flaky connection only has to retry the one chunk that failed, not the whole file
 *  - the server never has to hold a multi-hundred-MB request body in memory at once
 *
 * `isCancelled` is polled between chunks so the caller's Cancel button can stop an in-flight
 * upload; on cancellation the partially-uploaded session is aborted server-side (freeing its
 * chunk storage) before the cancellation error is thrown.
 */
export async function uploadFileInChunks(
  file: File,
  projectName: string,
  description: string,
  onProgress: (progress: ChunkUploadProgress) => void,
  isCancelled: () => boolean = () => false,
): Promise<ProjectResponse> {
  const session = await initUploadSession(file.name, file.size, projectName, description);
  const { sessionId, chunkSizeBytes, totalChunks } = session;

  // Resuming an existing session (e.g. after a page reload) would start here by querying
  // getUploadStatus and skipping already-received indices; for a freshly initiated session this
  // is simply empty, but the machinery is in place for that scenario too.
  const alreadyReceived = new Set((await getUploadStatus(sessionId)).receivedChunks);

  for (let index = 0; index < totalChunks; index++) {
    if (isCancelled()) {
      await abortUpload(sessionId).catch(() => undefined);
      throw new UploadCancelledError();
    }
    if (alreadyReceived.has(index)) {
      onProgress({ sentChunks: index + 1, totalChunks, percent: Math.round(((index + 1) / totalChunks) * 100) });
      continue;
    }

    const start = index * chunkSizeBytes;
    const end = Math.min(start + chunkSizeBytes, file.size);
    const chunk = file.slice(start, end);

    let lastError: unknown;
    let uploaded = false;
    for (let attempt = 1; attempt <= MAX_RETRIES_PER_CHUNK && !uploaded; attempt++) {
      try {
        await uploadChunk(sessionId, index, chunk);
        uploaded = true;
      } catch (err) {
        lastError = err;
        if (attempt < MAX_RETRIES_PER_CHUNK) {
          await delay(RETRY_BASE_DELAY_MS * attempt);
        }
      }
    }
    if (!uploaded) {
      throw lastError instanceof Error ? lastError : new Error(`Failed to upload chunk ${index} of ${totalChunks}`);
    }

    onProgress({ sentChunks: index + 1, totalChunks, percent: Math.round(((index + 1) / totalChunks) * 100) });
  }

  return completeUpload(sessionId);
}
