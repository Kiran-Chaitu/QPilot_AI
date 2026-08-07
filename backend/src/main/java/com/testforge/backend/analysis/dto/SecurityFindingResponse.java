package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.Severity;

import java.time.Instant;

public record SecurityFindingResponse(
        Long id, String category, Severity severity, String description,
        String recommendation, String location, Instant createdAt
) {
}
