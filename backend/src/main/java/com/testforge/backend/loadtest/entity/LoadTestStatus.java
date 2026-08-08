package com.testforge.backend.loadtest.entity;

/**
 * Lifecycle of a load test. These states are distinct in the UI on purpose: a run that was cancelled
 * mid-flight has partial-but-real metrics, which is a different thing from one that completed its
 * full plan, and both differ from one that never got off the ground.
 */
public enum LoadTestStatus {

    /** Accepted and validated, not yet started. */
    CONFIGURED,

    /** Generating traffic now. Metrics are partial and still moving. */
    RUNNING,

    /** Ran to the end of its configured plan. Metrics are final. */
    COMPLETED,

    /** Stopped early by the user. Metrics cover only the traffic actually sent before the stop. */
    CANCELLED,

    /** Could not run — e.g. the target was unresolvable from the first request onward. */
    FAILED
}
