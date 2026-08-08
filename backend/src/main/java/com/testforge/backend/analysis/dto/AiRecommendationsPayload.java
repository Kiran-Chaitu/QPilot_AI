package com.testforge.backend.analysis.dto;

import java.util.List;

/** Maps the JSON returned by the RECOMMENDATIONS agent (see {@code JsonSchemas.RECOMMENDATIONS}). */
public record AiRecommendationsPayload(
        List<Action> priorityActions,
        String testStrategy,
        String riskExplanation
) {
    public record Action(String title, String rationale, String effort) {
    }
}
