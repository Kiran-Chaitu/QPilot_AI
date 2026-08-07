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
 * Docs: https://ai.google.dev/api/generate-content
 */
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

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
            long latency = System.currentTimeMillis() - start;
            log.error("Gemini call failed for agent {}: {}", prompt.agentType(), ex.getMessage());
            return new AiResult(getName(), null, latency, false, ex.getMessage());
        }
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
