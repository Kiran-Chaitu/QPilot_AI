package com.testforge.backend.analysis.entity;

/**
 * Lifecycle of a generated test. The whole point of this enum is that "we wrote this test" and
 * "this test passed" are different claims: a generated test starts at {@link #GENERATED} and only
 * ever reaches an {@code EXECUTED_*} state after QPilot actually ran it and observed the outcome.
 * Nothing in the codebase may transition a test to {@link #EXECUTED_PASSED} without a real
 * execution record (status code, latency, timestamp) to back it up.
 */
public enum TestExecutionStatus {

    /** Test code exists and was persisted, but has not been run. This is the default. */
    GENERATED,

    /**
     * Cannot be run by QPilot at all — e.g. a unit test that needs the uploaded project's own build
     * toolchain, test dependencies and compilation step. Carries a reason so the UI can explain why
     * instead of implying failure.
     */
    NOT_EXECUTABLE,

    /** Runnable in principle, but a prerequisite was missing (e.g. no live target URL configured). */
    SKIPPED,

    /** Executed against a real target and all assertions held. */
    EXECUTED_PASSED,

    /** Executed against a real target and at least one assertion failed. */
    EXECUTED_FAILED,

    /** Execution was attempted but could not complete (connection refused, DNS failure, timeout). */
    EXECUTION_ERROR
}
