package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.ResultOrigin;
import com.testforge.backend.analysis.entity.Severity;

import java.time.Instant;

/**
 * A security finding as exposed to the frontend.
 *
 * <p>{@code origin} and {@code evidence} exist so the UI can be honest about what it is showing: a
 * STATIC_ANALYSIS row cites the file, line and exact source text that triggered it and is therefore
 * verifiable, while an AI_SUGGESTION row is advisory and is labelled as such.
 */
public record SecurityFindingResponse(
        Long id,
        String category,
        Severity severity,
        String description,
        String recommendation,
        String location,
        ResultOrigin origin,
        Integer lineNumber,
        String evidence,
        String ruleId,
        Integer occurrenceCount,
        Instant createdAt
) {
}
