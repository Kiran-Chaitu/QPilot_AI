package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.TestType;

import java.time.Instant;

public record GeneratedTestResponse(
        Long id, TestType type, String title, String targetName, String framework,
        String description, String code, Instant createdAt
) {
}
