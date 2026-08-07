package com.testforge.backend.ai.dto;

/**
 * A single request to an AI agent: what role to play (systemInstruction), what
 * to reason over (userContent), and the JSON shape the response must conform
 * to (jsonSchema - a Gemini/OpenAPI-style schema as a raw JSON string).
 */
public record AiPrompt(AgentType agentType, String systemInstruction, String userContent, String jsonSchema) {
}
