package com.testforge.backend.analysis.dto;

import java.util.List;

public record AnalysisResultResponse(
        AnalysisRunResponse run,
        List<GeneratedTestResponse> tests,
        List<SecurityFindingResponse> securityFindings,
        RiskAssessmentResponse risk
) {
}
