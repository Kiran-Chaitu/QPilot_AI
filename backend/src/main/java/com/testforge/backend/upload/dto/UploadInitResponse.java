package com.testforge.backend.upload.dto;

import java.time.Instant;

public record UploadInitResponse(
        String sessionId,
        long chunkSizeBytes,
        int totalChunks,
        Instant expiresAt
) {
}
