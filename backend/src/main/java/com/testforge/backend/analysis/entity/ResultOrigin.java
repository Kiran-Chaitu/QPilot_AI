package com.testforge.backend.analysis.entity;

/**
 * Where a stored analysis result actually came from. This distinction is load-bearing rather than
 * cosmetic: a finding produced by scanning real bytes on disk is evidence, whereas a finding
 * produced by a language model is a suggestion that may be wrong. The UI must never present the
 * second kind as if it were the first, so every finding/test row records its provenance and the
 * frontend labels it accordingly.
 */
public enum ResultOrigin {

    /** Derived deterministically from the project's real files — reproducible, cites file:line evidence. */
    STATIC_ANALYSIS,

    /** Produced by an external LLM from the project context. Advisory; not a measurement. */
    AI_SUGGESTION
}
