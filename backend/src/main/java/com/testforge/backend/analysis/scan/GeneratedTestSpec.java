package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.TestType;

/**
 * A test QPilot generated from real project facts, plus everything needed to decide whether QPilot
 * itself can run it.
 *
 * <p>The {@code executable} flag is the honest dividing line. An API test carrying a concrete method
 * and path can be fired at a live target and produce a genuine pass/fail. A unit-test scaffold
 * cannot: running it needs the uploaded project's own compiler, dependencies and test runner. Rather
 * than quietly reporting the second kind as passing, it is persisted as
 * {@code NOT_EXECUTABLE} with {@code notExecutableReason} shown in the UI.
 *
 * @param type                 test category
 * @param title                test name
 * @param targetName            the real class/method/endpoint under test
 * @param framework            framework the emitted code targets
 * @param description          what the test asserts
 * @param code                 the emitted test source
 * @param executable           true when QPilot can execute this itself (HTTP-level tests)
 * @param requestMethod        HTTP method for executable tests, else null
 * @param requestPath          path (relative to the project's base URL) for executable tests, else null
 * @param requestBody          body to send when executing, or null for bodyless requests
 * @param expectedStatusCodes  comma-separated status codes that constitute a pass
 * @param notExecutableReason  why QPilot cannot run this test, when {@code executable} is false
 */
public record GeneratedTestSpec(
        TestType type,
        String title,
        String targetName,
        String framework,
        String description,
        String code,
        boolean executable,
        String requestMethod,
        String requestPath,
        String requestBody,
        String expectedStatusCodes,
        String notExecutableReason
) {
}
