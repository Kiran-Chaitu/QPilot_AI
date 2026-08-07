package com.testforge.backend.ai.dto;

public record AiConfigResponse(
        String provider,
        boolean hasApiKey,
        String maskedApiKey,
        String model,
        String statusMessage
) {
}
