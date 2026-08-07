package com.testforge.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;
import com.testforge.backend.ai.provider.AiProvider;
import com.testforge.backend.ai.provider.GeminiProvider;
import com.testforge.backend.ai.provider.MockAiProvider;
import com.testforge.backend.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Facade the rest of the app calls into for AI reasoning. Resolves which
 * {@link AiProvider} to use at startup: Gemini if configured with an API key,
 * otherwise the deterministic mock provider so the whole platform stays
 * demoable without any external dependency.
 */
@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final AiProvider activeProvider;
    private final AiProvider mockFallback;

    public AiClient(RestClient.Builder restClientBuilder, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.mockFallback = new MockAiProvider(objectMapper);
        boolean wantsGemini = "gemini".equalsIgnoreCase(aiProperties.getProvider());
        boolean hasApiKey = aiProperties.getGemini().getApiKey() != null && !aiProperties.getGemini().getApiKey().isBlank();

        if (wantsGemini && hasApiKey) {
            this.activeProvider = new GeminiProvider(restClientBuilder, aiProperties, objectMapper);
            log.info("AI TestPilot is using the Gemini provider (model={})", aiProperties.getGemini().getModel());
        } else {
            this.activeProvider = mockFallback;
            if (wantsGemini) {
                log.warn("app.ai.provider=gemini but no GEMINI_API_KEY configured; falling back to the mock AI provider.");
            } else {
                log.info("AI TestPilot is using the mock AI provider (offline demo mode). "
                        + "Set AI_PROVIDER=gemini and GEMINI_API_KEY to use real Gemini calls.");
            }
        }
    }

    public AiResult run(AiPrompt prompt) {
        return activeProvider.generate(prompt);
    }

    public String getActiveProviderName() {
        return activeProvider.getName();
    }
}
