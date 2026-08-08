package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.AnalysisStatus;

import java.time.Instant;
import java.util.List;

/**
 * One analysis run's status and narrative output.
 *
 * <p>The static and AI halves are reported separately on purpose. {@code staticSummary} and
 * {@code observations} are derived from measured file counts and rule matches, so they are always
 * present. {@code aiSummary} is present only when an AI provider is configured and responded; when it
 * is absent, {@code aiStatus} explains why rather than leaving the UI to imply the analysis was
 * incomplete or to silently pass off static output as AI insight.
 *
 * @param progressPercent coarse progress for the polling UI while {@code status} is RUNNING
 * @param currentStage    human-readable description of the stage in flight
 */
public record AnalysisRunResponse(
        Long id,
        AnalysisStatus status,
        String staticSummary,
        List<String> observations,
        String aiSummary,
        List<String> aiKeyResponsibilities,
        List<String> aiNotableObservations,
        String aiStatus,
        String aiProvider,
        boolean aiEnabled,
        int progressPercent,
        String currentStage,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
}
