package com.testforge.backend.project.dto;

import com.testforge.backend.project.entity.ProjectSourceType;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
        String name,
        String description,
        @NotNull ProjectSourceType sourceType,
        String repoUrl,
        String targetUrl,
        String targetApiUrl
) {
}
