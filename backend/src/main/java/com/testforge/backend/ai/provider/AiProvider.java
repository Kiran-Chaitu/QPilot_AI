package com.testforge.backend.ai.provider;

import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;

/**
 * A pluggable AI backend. Spring Boot only orchestrates; implementations of
 * this interface are the only place that actually calls out to an LLM.
 */
public interface AiProvider {

    AiResult generate(AiPrompt prompt);

    String getName();
}
