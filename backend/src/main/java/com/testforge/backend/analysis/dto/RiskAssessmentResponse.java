package com.testforge.backend.analysis.dto;

import java.time.Instant;
import java.util.List;

public record RiskAssessmentResponse(
        int score, List<String> reasons, int coverageEstimatePercent, List<String> coverageGaps, Instant createdAt
) {
}
