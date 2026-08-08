package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.ResultOrigin;
import com.testforge.backend.analysis.entity.TestExecutionStatus;
import com.testforge.backend.analysis.entity.TestType;

import java.time.Instant;

/**
 * A generated test plus its real execution record, if any.
 *
 * <p>{@code executionStatus} is the field the UI must key off — never the mere existence of the row.
 * {@code observedHttpStatus}, {@code executionLatencyMs} and {@code lastExecutedAt} are populated only
 * by an actual execution, so a caller can always tell a measured result from an unrun one.
 */
public record GeneratedTestResponse(
        Long id,
        TestType type,
        String title,
        String targetName,
        String framework,
        String description,
        String code,
        ResultOrigin origin,
        TestExecutionStatus executionStatus,
        String executionDetail,
        Instant lastExecutedAt,
        Long executionLatencyMs,
        Integer observedHttpStatus,
        String requestMethod,
        String requestPath,
        String expectedStatusCodes,
        Instant createdAt
) {
}
