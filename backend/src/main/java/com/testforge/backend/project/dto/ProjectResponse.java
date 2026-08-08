package com.testforge.backend.project.dto;

import com.testforge.backend.project.entity.ProjectSourceType;
import com.testforge.backend.project.entity.ProjectStatus;

import java.time.Instant;

/**
 * A project as exposed to the frontend.
 *
 * @param fileCount      files actually indexed. Zero for URL-based projects, because nothing was
 *                       downloaded — this is a real count, not a placeholder.
 * @param discoveryNotes for URL-based projects, what discovery found and what it could not, so an empty
 *                       structure is explained rather than merely blank
 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        ProjectSourceType sourceType,
        String repoUrl,
        String targetUrl,
        String targetApiUrl,
        String primaryLanguage,
        Integer fileCount,
        ProjectStatus status,
        boolean hasSwaggerSpec,
        String processingError,
        String discoveryNotes,
        Instant createdAt,
        Instant updatedAt
) {
}
