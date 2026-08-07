package com.testforge.backend.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Client announces the file it's about to upload; server decides chunk size and session TTL. */
public record UploadInitRequest(
        @NotBlank String fileName,
        @Positive long fileSizeBytes,
        String projectName,
        String description
) {
}
