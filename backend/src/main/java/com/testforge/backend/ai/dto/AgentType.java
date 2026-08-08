package com.testforge.backend.ai.dto;

/**
 * The AI agents in the enrichment pipeline.
 *
 * <p>Note what is absent: there is no risk-scoring or coverage-estimating agent. Those numbers are now
 * computed by {@code StaticAnalysisEngine} from counted facts, because asking a language model to
 * "estimate coverage" produces a confident-looking number with nothing behind it. The agents that
 * remain do work an LLM is genuinely suited to — explaining code in prose, proposing additional cases
 * to consider, and reasoning about weaknesses — and everything they return is stored and displayed as
 * a suggestion.
 */
public enum AgentType {

    /** Narrative explanation of what the project does, from its structure and key file excerpts. */
    CODE_SUMMARY,

    /** Additional test scenarios worth covering, beyond those derivable from route scanning. */
    TEST_GENERATION,

    /** Suggested security weaknesses to investigate, reviewed by a human before being trusted. */
    SECURITY_ANALYSIS,

    /** Prioritized testing/quality recommendations grounded in the measured scan results. */
    RECOMMENDATIONS
}
