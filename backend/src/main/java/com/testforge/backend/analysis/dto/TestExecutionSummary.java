package com.testforge.backend.analysis.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of really executing a project's executable tests against its live target.
 *
 * <p>The counts are deliberately split so the UI never has to conflate them: {@code totalTests} is how
 * many tests exist, {@code executed} is how many actually ran, and only executed tests can be
 * {@code passed} or {@code failed}. A project with 40 generated tests and no reachable target reports
 * 40 total / 0 executed / 0 passed — not "40 passed".
 *
 * @param baseUrl        base URL the requests were sent to, or null when nothing could be executed
 * @param totalTests     tests considered in this run
 * @param executed       tests that produced a real HTTP response (or a real transport error)
 * @param passed         executed tests whose observed status matched the expected set
 * @param failed         executed tests whose observed status did not match
 * @param errored        tests whose request could not complete at all (DNS, refused, timeout)
 * @param skipped        tests not attempted, with reasons carried on each test row
 * @param notExecutable  tests QPilot structurally cannot run (unit scaffolds)
 * @param durationMs     measured wall-clock duration of the whole execution pass
 * @param executedAt     when the pass ran
 * @param results        per-test outcomes
 */
public record TestExecutionSummary(
        String baseUrl,
        int totalTests,
        int executed,
        int passed,
        int failed,
        int errored,
        int skipped,
        int notExecutable,
        long durationMs,
        Instant executedAt,
        List<GeneratedTestResponse> results
) {
}
