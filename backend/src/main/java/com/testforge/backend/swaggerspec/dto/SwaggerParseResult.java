package com.testforge.backend.swaggerspec.dto;

import java.util.List;

public record SwaggerParseResult(
        String title,
        String version,
        List<SwaggerEndpointSummary> endpoints
) {
}
