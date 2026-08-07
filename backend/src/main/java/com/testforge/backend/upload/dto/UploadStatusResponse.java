package com.testforge.backend.upload.dto;

import com.testforge.backend.upload.entity.UploadSessionStatus;

import java.util.List;

/** Progress snapshot for a chunked upload session, polled by the client for resumability/progress. */
public record UploadStatusResponse(
        String sessionId,
        UploadSessionStatus status,
        int totalChunks,
        List<Integer> receivedChunks,
        Long resultProjectId,
        String errorMessage
) {
}
