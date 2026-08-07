package com.testforge.backend.swaggerspec.dto;

import java.util.List;

public record SwaggerEndpointSummary(
        String httpMethod,
        String path,
        String operationId,
        String summary,
        List<String> parameters,
        String requestBodyDescription,
        List<String> responseCodes
) {
}
