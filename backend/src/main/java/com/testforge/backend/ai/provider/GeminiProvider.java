package com.testforge.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;
import com.testforge.backend.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini "generateContent" REST endpoint directly (no SDK), asking
 * for structured JSON output via generationConfig.responseSchema so responses
 * can be parsed reliably into our domain DTOs.
 *
 * Includes retry-with-exponential-backoff for 429 (rate limit) and 503 (overloaded)
 * responses, parsing the retryDelay from the Gemini error JSON when available.
 *
 * Docs: https://ai.google.dev/api/generate-content
 */
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 30_000; // 30 seconds default

    private final RestClient restClient;
    private final AiProperties.Gemini config;
    private final ObjectMapper objectMapper;

    public GeminiProvider(RestClient.Builder restClientBuilder, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.config = aiProperties.getGemini();
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(config.getBaseUrl())
                .requestFactory(clientRequestFactory())
                .build();
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        int timeoutMs = (int) Duration.ofSeconds(config.getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }

    @Override
    public AiResult generate(AiPrompt prompt) {
        long start = System.currentTimeMillis();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> body = buildRequestBody(prompt);

                String responseBody = restClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/models/{model}:generateContent")
                                .queryParam("key", config.getApiKey())
                                .build(config.getModel()))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                String text = extractText(responseBody);
                long latency = System.currentTimeMillis() - start;
                return new AiResult(getName(), text, latency, true, null);

            } catch (Exception ex) {
                lastException = ex;
                String errorMsg = ex.getMessage() != null ? ex.getMessage() : "";

                // Check if this is a retryable error (429 rate limit or 503 overloaded)
                boolean isRetryable = errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED")
                        || errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE")
                        || errorMsg.contains("Too Many Requests");

                if (isRetryable && attempt < MAX_RETRIES) {
                    long delayMs = parseRetryDelay(errorMsg);
                    log.warn("Gemini rate limited for agent {} (attempt {}/{}). Retrying in {}s...",
                            prompt.agentType(), attempt + 1, MAX_RETRIES, delayMs / 1000);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // Non-retryable error or max retries exceeded
                    break;
                }
            }
        }

        long latency = System.currentTimeMillis() - start;
        String errorMessage = lastException != null ? lastException.getMessage() : "Unknown error";
        log.error("Gemini call failed for agent {} after {} attempts: {}", prompt.agentType(), MAX_RETRIES + 1, errorMessage);
        return new AiResult(getName(), null, latency, false, errorMessage);
    }

    /**
     * Attempts to parse the retryDelay from a Gemini 429 error response.
     * The error JSON typically contains: {"error": {..., "details": [..., {"@type": "...RetryInfo", "retryDelay": "30s"}]}}
     * Falls back to DEFAULT_RETRY_DELAY_MS if parsing fails.
     */
    private long parseRetryDelay(String errorMessage) {
        try {
            // Try to find retryDelay pattern like "retryDelay":"30s" or retryDelay: 26s
            int retryIdx = errorMessage.indexOf("retryDelay");
            if (retryIdx >= 0) {
                String after = errorMessage.substring(retryIdx);
                // Extract the numeric value — look for pattern like "30s" or "26.358s"
                StringBuilder digits = new StringBuilder();
                boolean foundDigit = false;
                for (char c : after.toCharArray()) {
                    if (Character.isDigit(c) || c == '.') {
                        digits.append(c);
                        foundDigit = true;
                    } else if (foundDigit && (c == 's' || c == 'S')) {
                        break;
                    } else if (foundDigit) {
                        break;
                    }
                }
                if (!digits.isEmpty()) {
                    double seconds = Double.parseDouble(digits.toString());
                    return (long) (seconds * 1000) + 2000; // Add 2s buffer
                }
            }
        } catch (Exception ignored) {
            // Fall through to default
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private Map<String, Object> buildRequestBody(AiPrompt prompt) throws Exception {
        Map<String, Object> generationConfig = new java.util.HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.4);
        if (prompt.jsonSchema() != null) {
            JsonNode schema = objectMapper.readTree(prompt.jsonSchema());
            generationConfig.put("responseSchema", schema);
        }

        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.systemInstruction()))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt.userContent())))),
                "generationConfig", generationConfig
        );
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response had no candidates: " + responseBody);
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini response had no content parts: " + responseBody);
        }
        return parts.get(0).path("text").asText();
    }

    @Override
    public String getName() {
        return "gemini:" + config.getModel();
    }
}
