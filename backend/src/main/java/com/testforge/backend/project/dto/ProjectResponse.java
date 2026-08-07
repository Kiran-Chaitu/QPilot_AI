package com.testforge.backend.project.dto;

import com.testforge.backend.project.entity.ProjectSourceType;
import com.testforge.backend.project.entity.ProjectStatus;

import java.time.Instant;

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
        Instant createdAt,
        Instant updatedAt
) {
}
