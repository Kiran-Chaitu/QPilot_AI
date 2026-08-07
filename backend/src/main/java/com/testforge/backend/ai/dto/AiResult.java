package com.testforge.backend.ai.dto;

/** Result of one AI agent call: the raw JSON text plus bookkeeping for the ai_requests audit log. */
public record AiResult(String providerName, String rawJson, long latencyMs, boolean success, String errorMessage) {
}
