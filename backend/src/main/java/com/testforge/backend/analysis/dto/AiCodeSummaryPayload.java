package com.testforge.backend.analysis.dto;

import java.util.List;

/** Maps the raw JSON returned by the CODE_SUMMARY agent (see JsonSchemas.CODE_SUMMARY). */
public record AiCodeSummaryPayload(String summary, List<String> keyResponsibilities, List<String> notableObservations) {
}
