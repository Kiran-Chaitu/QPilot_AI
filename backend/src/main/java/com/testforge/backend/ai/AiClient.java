package com.testforge.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.dto.AiConfigRequest;
import com.testforge.backend.ai.dto.AiConfigResponse;
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

@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient.Builder restClientBuilder;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiProvider mockFallback;

    private AiProvider activeProvider;
    private String customApiKey;
    private String customModel;

    public AiClient(RestClient.Builder restClientBuilder, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.mockFallback = new MockAiProvider(objectMapper);
        rebuildProvider();
    }

    public synchronized void updateConfig(AiConfigRequest req) {
        if (req.apiKey() != null) {
            this.customApiKey = req.apiKey().trim();
        }
        if (req.model() != null && !req.model().isBlank()) {
            this.customModel = req.model().trim();
        }
        rebuildProvider();
    }

    private void rebuildProvider() {
        String apiKey = (customApiKey != null && !customApiKey.isBlank())
                ? customApiKey
                : aiProperties.getGemini().getApiKey();

        String model = (customModel != null && !customModel.isBlank())
                ? customModel
                : aiProperties.getGemini().getModel();

        boolean hasKey = apiKey != null && !apiKey.isBlank();

        if (hasKey) {
            AiProperties.Gemini geminiConfig = new AiProperties.Gemini();
            geminiConfig.setApiKey(apiKey);
            geminiConfig.setModel(model);
            geminiConfig.setBaseUrl(aiProperties.getGemini().getBaseUrl());
            geminiConfig.setTimeoutSeconds(aiProperties.getGemini().getTimeoutSeconds());

            AiProperties customProperties = new AiProperties();
            customProperties.setProvider("gemini");
            customProperties.setGemini(geminiConfig);

            this.activeProvider = new GeminiProvider(restClientBuilder, customProperties, objectMapper);
            log.info("AI TestPilot active provider set to Gemini ({})", model);
        } else {
            this.activeProvider = mockFallback;
            log.info("AI TestPilot active provider set to Smart Offline Engine");
        }
    }

    public AiResult run(AiPrompt prompt) {
        return activeProvider.generate(prompt);
    }

    public String getActiveProviderName() {
        return activeProvider.getName();
    }

    public AiConfigResponse getConfigResponse() {
        boolean isGemini = activeProvider instanceof GeminiProvider;
        String key = customApiKey != null ? customApiKey : aiProperties.getGemini().getApiKey();
        boolean hasKey = key != null && !key.isBlank();
        String masked = hasKey && key.length() > 8 ? key.substring(0, 4) + "..." + key.substring(key.length() - 4) : (hasKey ? "****" : "None");
        String model = customModel != null ? customModel : aiProperties.getGemini().getModel();

        String msg = isGemini
                ? "Connected to Gemini AI (" + model + "). Real multimodal reasoning enabled."
                : "Using Smart Offline AI Engine. Enter a Gemini API Key above to activate live Gemini AI reasoning.";

        return new AiConfigResponse(
                isGemini ? "gemini" : "smart-offline",
                hasKey,
                masked,
                model,
                msg
        );
    }
}
