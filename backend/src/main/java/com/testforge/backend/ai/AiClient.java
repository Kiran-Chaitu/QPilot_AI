package com.testforge.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.dto.AiConfigRequest;
import com.testforge.backend.ai.dto.AiConfigResponse;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;
import com.testforge.backend.ai.provider.AiProvider;
import com.testforge.backend.ai.provider.GeminiProvider;
import com.testforge.backend.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Gateway to the configured LLM provider.
 *
 * <p>There is deliberately no offline "mock" provider behind this class any more. The previous
 * implementation silently swapped in a generator that fabricated security findings, derived a risk
 * score from the length of the project's name, and emitted test code referencing classes that did not
 * exist — all of it surfaced in the UI as if it were analysis. Removing it means an unconfigured
 * install now says "no AI provider configured" and shows only measured static-analysis results, which
 * is the truthful outcome.
 *
 * <p>{@link #isConfigured()} lets callers branch on availability instead of receiving invented
 * content. When it returns false, {@link #run(AiPrompt)} fails fast with an explanatory message rather
 * than pretending to answer.
 */
@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private static final String NOT_CONFIGURED_MESSAGE =
            "No AI provider is configured. Add a Gemini API key (Settings → AI Configuration, or the "
                    + "GEMINI_API_KEY environment variable) to enable AI narrative analysis. Static analysis, "
                    + "live audits, load testing and rate-limit testing all run without it.";

    private final RestClient.Builder restClientBuilder;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * Reassigned when configuration changes at runtime, and read from request threads — volatile so a
     * key update is visible to in-flight callers without a lock on the hot path.
     */
    private volatile AiProvider activeProvider;
    private volatile String activeModel;

    private String customApiKey;
    private String customModel;

    public AiClient(RestClient.Builder restClientBuilder, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        rebuildProvider();
    }

    public synchronized void updateConfig(AiConfigRequest req) {
        if (req.apiKey() != null) {
            String trimmed = req.apiKey().trim();
            // An empty string is a deliberate "remove the key" instruction, not a no-op.
            this.customApiKey = trimmed.isEmpty() ? null : trimmed;
        }
        if (req.model() != null && !req.model().isBlank()) {
            this.customModel = req.model().trim();
        }
        rebuildProvider();
    }

    private void rebuildProvider() {
        String apiKey = firstNonBlank(customApiKey, aiProperties.getGemini().getApiKey());
        String model = firstNonBlank(customModel, aiProperties.getGemini().getModel());
        this.activeModel = model;

        if (apiKey == null) {
            this.activeProvider = null;
            log.info("No AI provider configured — QPilot will run static analysis only and report AI as unavailable");
            return;
        }

        AiProperties.Gemini geminiConfig = new AiProperties.Gemini();
        geminiConfig.setApiKey(apiKey);
        geminiConfig.setModel(model);
        geminiConfig.setBaseUrl(aiProperties.getGemini().getBaseUrl());
        geminiConfig.setTimeoutSeconds(aiProperties.getGemini().getTimeoutSeconds());

        AiProperties customProperties = new AiProperties();
        customProperties.setProvider("gemini");
        customProperties.setGemini(geminiConfig);

        this.activeProvider = new GeminiProvider(restClientBuilder, customProperties, objectMapper);
        log.info("AI provider configured: Gemini ({})", model);
    }

    /** True when a real provider is available to call. */
    public boolean isConfigured() {
        return activeProvider != null;
    }

    /**
     * Runs one agent prompt. When no provider is configured this returns an unsuccessful
     * {@link AiResult} carrying {@link #NOT_CONFIGURED_MESSAGE}; it never returns fabricated content.
     */
    public AiResult run(AiPrompt prompt) {
        AiProvider provider = activeProvider;
        if (provider == null) {
            return new AiResult("none", null, 0, false, NOT_CONFIGURED_MESSAGE);
        }
        return provider.generate(prompt);
    }

    /** Provider identifier for audit logging, or {@code "none"} when AI is unavailable. */
    public String getActiveProviderName() {
        AiProvider provider = activeProvider;
        return provider != null ? provider.getName() : "none";
    }

    public String getNotConfiguredMessage() {
        return NOT_CONFIGURED_MESSAGE;
    }

    public AiConfigResponse getConfigResponse() {
        String key = firstNonBlank(customApiKey, aiProperties.getGemini().getApiKey());
        boolean hasKey = key != null;
        // Show only the last 4 characters. Echoing a prefix as well (as this used to) hands an
        // attacker with read access a meaningful head start on the secret for no usability gain.
        String masked = hasKey
                ? (key.length() > 4 ? "••••" + key.substring(key.length() - 4) : "••••")
                : "Not set";

        String message = hasKey
                ? "Connected to Gemini (" + activeModel + "). AI narrative analysis and suggestions are enabled, "
                        + "and are labelled separately from measured static-analysis results."
                : NOT_CONFIGURED_MESSAGE;

        return new AiConfigResponse(hasKey ? "gemini" : "none", hasKey, masked, activeModel, message);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return (fallback != null && !fallback.isBlank()) ? fallback : null;
    }
}
