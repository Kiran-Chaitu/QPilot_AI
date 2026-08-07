package com.testforge.backend.analysis.dto;

import java.util.List;

public record AiRiskScorePayload(int score, List<String> reasons, int coverageEstimatePercent, List<String> coverageGaps) {
}
