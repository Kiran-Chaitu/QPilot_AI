package com.testforge.backend.analysis.dto;

import com.testforge.backend.analysis.entity.AnalysisStatus;

import java.time.Instant;
import java.util.List;

public record AnalysisRunResponse(
        Long id, AnalysisStatus status, String codeSummary, List<String> keyResponsibilities,
        List<String> notableObservations, String errorMessage, Instant startedAt, Instant completedAt,
        String aiProvider
) {
}
