package com.testforge.backend.ai.dto;

public record AiConfigRequest(
        String apiKey,
        String model,
        String provider
) {
}
