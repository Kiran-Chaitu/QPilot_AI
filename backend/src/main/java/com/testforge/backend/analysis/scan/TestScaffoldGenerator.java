package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.TestType;
import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerEndpointSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits tests from what the project actually contains.
 *
 * <p>Every test produced here names a route, class or function that was discovered by scanning the
 * uploaded source or by parsing its OpenAPI document. Nothing is templated from an invented
 * {@code FooService} or a placeholder {@code /api/v1/process} path — if the project exposes three
 * endpoints, three endpoints get API tests; if it exposes none, no API tests are emitted and the
 * reason is reported instead.
 *
 * <p>The generator also decides, per test, whether QPilot can execute it. HTTP-level tests carry a
 * concrete method/path and are marked executable, so {@code ApiTestExecutionService} can run them
 * against a live target and record a real outcome. Unit scaffolds are marked non-executable with a
 * stated reason, because compiling and running arbitrary uploaded code is out of scope.
 */
@Service
public class TestScaffoldGenerator {

    private static final int MAX_ENDPOINT_TESTS = 40;
    private static final int MAX_UNIT_TESTS = 15;

    private static final Pattern JVM_CLASS = Pattern.compile(
            "(?:public\\s+)?(?:final\\s+)?(?:class|record|interface)\\s+(\\w+)");
    private static final Pattern JVM_PUBLIC_METHOD = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?([\\w<>,\\[\\]\\s.?]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern JS_EXPORTED = Pattern.compile(
            "export\\s+(?:default\\s+)?(?:async\\s+)?function\\s+(\\w+)\\s*\\(([^)]*)\\)|"
                    + "export\\s+const\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*=>");
    private static final Pattern PY_DEF = Pattern.compile(
            "^\\s*def\\s+(?!_)(\\w+)\\s*\\(([^)]*)\\)", Pattern.MULTILINE);

    private static final Set<String> NOISE_METHOD_NAMES = Set.of(
            "toString", "equals", "hashCode", "main", "builder", "of", "valueOf", "getClass", "clone");

    /** Intentionally-truncated JSON: the point is that the server must reject it as a 4xx, not 500. */
    private static final String MALFORMED_JSON_BODY = "{\"malformed\":";

    /**
     * A classic SQL tautology sent as an ordinary field value. This is a read-only probe — it asserts
     * on the response status only and never attempts a destructive statement.
     */
    private static final String SQL_PROBE_BODY = "{\"q\":\"' OR '1'='1\"}";

    /**
     * Why an HTTP test cannot be executed when the project has no live target. The test code is still
     * generated and downloadable — it simply has nowhere to run, which is a materially different
     * statement from "it failed".
     */
    private static final String NO_TARGET_REASON =
            "No live base URL is configured for this project, so QPilot has nothing to send the request to. "
                    + "Set the project's target API URL (or website URL) to enable real execution — the generated "
                    + "test code is complete and can also be run in your own pipeline via the API_BASE_URL "
                    + "environment variable.";

    /**
     * @param structure    statically-analyzed project structure (real endpoints, real key-file excerpts)
     * @param swagger      parsed OpenAPI document, if one was uploaded or discovered; may be null
     * @param baseUrlKnown whether a live base URL exists for this project — decides whether HTTP tests
     *                     are marked executable or skipped-for-lack-of-target
     */
    public List<GeneratedTestSpec> generate(ProjectStructureSummary structure, SwaggerParseResult swagger,
                                            boolean baseUrlKnown, String primaryLanguage) {
        List<GeneratedTestSpec> specs = new ArrayList<>();

        List<EndpointFact> endpoints = mergeEndpoints(structure, swagger);
        String apiFramework = apiFrameworkFor(primaryLanguage);

        int emitted = 0;
        for (EndpointFact endpoint : endpoints) {
            if (emitted >= MAX_ENDPOINT_TESTS) {
                break;
            }
            specs.add(happyPathTest(endpoint, apiFramework, baseUrlKnown));
            emitted++;
        }

        // Negative/security cases are emitted against real endpoints too, but only a handful, chosen
        // from routes most likely to be protected — one per category rather than per endpoint, so the
        // suite stays reviewable instead of ballooning to hundreds of near-identical cases.
        endpoints.stream()
                .filter(e -> looksProtected(e.path()))
                .findFirst()
                .ifPresent(e -> specs.add(unauthenticatedAccessTest(e, apiFramework, baseUrlKnown)));

        endpoints.stream()
                .filter(e -> e.method().equalsIgnoreCase("POST") || e.method().equalsIgnoreCase("PUT"))
                .findFirst()
                .ifPresent(e -> {
                    specs.add(malformedPayloadTest(e, apiFramework, baseUrlKnown));
                    specs.add(injectionProbeTest(e, apiFramework, baseUrlKnown));
                });

        endpoints.stream()
                .filter(e -> e.path().contains("{") || e.path().contains(":"))
                .findFirst()
                .ifPresent(e -> specs.add(pathParameterEdgeCaseTest(e, apiFramework, baseUrlKnown)));

        specs.addAll(unitScaffolds(structure, primaryLanguage));

        return specs;
    }

    // ─── Endpoint facts ──────────────────────────────────────────────────────────

    private record EndpointFact(String method, String path, String source, String summary) {
    }

    private List<EndpointFact> mergeEndpoints(ProjectStructureSummary structure, SwaggerParseResult swagger) {
        // Keyed by METHOD+path so a route declared in code and also present in the OpenAPI document
        // yields one test rather than two. The OpenAPI entry wins because it carries a human summary.
        Map<String, EndpointFact> byKey = new LinkedHashMap<>();

        if (structure != null) {
            for (ApiEndpointSummary endpoint : structure.endpoints()) {
                String method = normalizeMethod(endpoint.httpMethod());
                String path = normalizePath(endpoint.path());
                if (path == null) {
                    continue;
                }
                byKey.putIfAbsent(method + " " + path,
                        new EndpointFact(method, path, endpoint.sourceFile(), null));
            }
        }
        if (swagger != null) {
            for (SwaggerEndpointSummary endpoint : swagger.endpoints()) {
                String method = normalizeMethod(endpoint.httpMethod());
                String path = normalizePath(endpoint.path());
                if (path == null) {
                    continue;
                }
                byKey.put(method + " " + path, new EndpointFact(method, path,
                        "OpenAPI: " + swagger.title(), endpoint.summary()));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private String normalizeMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return "GET";
        }
        // Flask-style declarations can carry several methods in one annotation ("GET,POST").
        String first = raw.split(",")[0].trim().toUpperCase(Locale.ROOT);
        return first.isEmpty() ? "GET" : first;
    }

    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String path = raw.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private boolean looksProtected(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("admin") || lower.contains("user") || lower.contains("account")
                || lower.contains("profile") || lower.contains("me") || lower.contains("order")
                || lower.contains("payment") || lower.contains("dashboard");
    }

    private String apiFrameworkFor(String primaryLanguage) {
        if (primaryLanguage == null) {
            return "HTTP (language-agnostic)";
        }
        return switch (primaryLanguage) {
            case "Java", "Kotlin", "Scala" -> "REST Assured / JUnit 5";
            case "TypeScript", "JavaScript" -> "Jest / Supertest";
            case "Python" -> "PyTest / requests";
            case "Go" -> "Go testing / net/http";
            case "C#" -> "xUnit / HttpClient";
            default -> "HTTP (language-agnostic)";
        };
    }

    // ─── HTTP test emitters (all reference real methods and real paths) ──────────

    private GeneratedTestSpec happyPathTest(EndpointFact endpoint, String framework, boolean baseUrlKnown) {
        String title = testName("should_respond_successfully", endpoint);
        String expected = endpoint.method().equals("POST") ? "200,201,202" : "200,204";
        String description = "Verifies " + endpoint.method() + " " + endpoint.path()
                + " responds with a success status."
                + (endpoint.summary() != null && !endpoint.summary().isBlank()
                        ? " Documented purpose: " + endpoint.summary() : "")
                + " Route discovered in " + endpoint.source() + ".";
        return new GeneratedTestSpec(TestType.API, title, endpoint.method() + " " + endpoint.path(),
                framework, description,
                emitHttpTest(framework, title, endpoint.method(), endpoint.path(), null,
                        "expect a 2xx status", expected),
                baseUrlKnown, endpoint.method(), endpoint.path(), null, expected,
                baseUrlKnown ? null : NO_TARGET_REASON);
    }

    private GeneratedTestSpec unauthenticatedAccessTest(EndpointFact endpoint, String framework, boolean baseUrlKnown) {
        String title = testName("should_reject_unauthenticated_access", endpoint);
        String expected = "401,403";
        return new GeneratedTestSpec(TestType.SECURITY, title, endpoint.method() + " " + endpoint.path(),
                framework,
                "Calls " + endpoint.method() + " " + endpoint.path() + " with no Authorization header. A route "
                        + "handling user- or account-scoped data must answer 401/403 rather than serving data. "
                        + "Route discovered in " + endpoint.source() + ".",
                emitHttpTest(framework, title, endpoint.method(), endpoint.path(), null,
                        "expect 401 or 403 when no credentials are supplied", expected),
                baseUrlKnown, endpoint.method(), endpoint.path(), null, expected,
                baseUrlKnown ? null : NO_TARGET_REASON);
    }

    private GeneratedTestSpec malformedPayloadTest(EndpointFact endpoint, String framework, boolean baseUrlKnown) {
        String title = testName("should_reject_malformed_payload", endpoint);
        String expected = "400,415,422";
        return new GeneratedTestSpec(TestType.EDGE_CASE, title, endpoint.method() + " " + endpoint.path(),
                framework,
                "Sends syntactically invalid JSON to " + endpoint.method() + " " + endpoint.path()
                        + ". The endpoint should reject it with a 4xx client error rather than returning 500, "
                        + "which would indicate the parse failure escaped as an unhandled exception.",
                emitHttpTest(framework, title, endpoint.method(), endpoint.path(), MALFORMED_JSON_BODY,
                        "expect a 4xx client error, never 500", expected),
                baseUrlKnown, endpoint.method(), endpoint.path(), MALFORMED_JSON_BODY, expected,
                baseUrlKnown ? null : NO_TARGET_REASON);
    }

    private GeneratedTestSpec injectionProbeTest(EndpointFact endpoint, String framework, boolean baseUrlKnown) {
        String title = testName("should_not_execute_injected_sql", endpoint);
        String expected = "400,401,403,404,422";
        return new GeneratedTestSpec(TestType.SECURITY, title, endpoint.method() + " " + endpoint.path(),
                framework,
                "Submits a classic SQL tautology payload to " + endpoint.method() + " " + endpoint.path()
                        + ". A parameterized implementation treats it as an ordinary string and rejects it as "
                        + "invalid input; a 200 with data, or a 500, both suggest the value reached the query.",
                emitHttpTest(framework, title, endpoint.method(), endpoint.path(), SQL_PROBE_BODY,
                        "expect the payload to be rejected as data, not executed", expected),
                baseUrlKnown, endpoint.method(), endpoint.path(), SQL_PROBE_BODY, expected,
                baseUrlKnown ? null : NO_TARGET_REASON);
    }

    private GeneratedTestSpec pathParameterEdgeCaseTest(EndpointFact endpoint, String framework, boolean baseUrlKnown) {
        String title = testName("should_handle_nonexistent_resource_id", endpoint);
        String expected = "400,404";
        String concretePath = endpoint.path()
                .replaceAll("\\{[^}]+}", "999999999")
                .replaceAll(":[A-Za-z_]+", "999999999");
        return new GeneratedTestSpec(TestType.EDGE_CASE, title, endpoint.method() + " " + endpoint.path(),
                framework,
                "Requests " + concretePath + " — a well-formed but almost certainly nonexistent identifier. "
                        + "Expect a clean 404 (or 400 if the id fails validation) rather than a 500 or an empty 200.",
                emitHttpTest(framework, title, endpoint.method(), concretePath, null,
                        "expect 404/400 for a missing resource", expected),
                baseUrlKnown, endpoint.method(), concretePath, null, expected,
                baseUrlKnown ? null : NO_TARGET_REASON);
    }

    private String testName(String prefix, EndpointFact endpoint) {
        String slug = endpoint.path()
                .replaceAll("[{}:]", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "root";
        }
        return prefix + "_" + endpoint.method().toLowerCase(Locale.ROOT) + "_" + slug;
    }

    /**
     * Emits idiomatic HTTP test source for the project's own ecosystem.
     *
     * <p>{@code body} is raw request text (deliberately including the intentionally-invalid JSON used
     * by the malformed-payload probe), so each emitter escapes it for its own host language rather
     * than assuming it arrives pre-escaped.
     */
    private String emitHttpTest(String framework, String testName, String method, String path,
                                String body, String intent, String expectedCodes) {
        String codes = String.join(", ", expectedCodes.split(","));
        if (framework.startsWith("REST Assured")) {
            StringBuilder sb = new StringBuilder();
            sb.append("import io.restassured.http.ContentType;\n")
                    .append("import org.junit.jupiter.api.Test;\n\n")
                    .append("import static io.restassured.RestAssured.given;\n")
                    .append("import static org.hamcrest.Matchers.oneOf;\n\n")
                    .append("class ").append(pascal(testName)).append("Test {\n\n")
                    .append("    // ").append(intent).append("\n")
                    .append("    @Test\n    void ").append(testName).append("() {\n")
                    .append("        given()\n")
                    .append("            .baseUri(System.getenv(\"API_BASE_URL\"))\n")
                    .append("            .contentType(ContentType.JSON)\n");
            if (body != null) {
                sb.append("            .body(\"").append(escapeForJavaLiteral(body)).append("\")\n");
            }
            sb.append("        .when()\n")
                    .append("            .").append(method.toLowerCase(Locale.ROOT))
                    .append("(\"").append(path).append("\")\n")
                    .append("        .then()\n")
                    .append("            .statusCode(oneOf(").append(codes).append("));\n")
                    .append("    }\n}\n");
            return sb.toString();
        }
        if (framework.startsWith("Jest")) {
            StringBuilder sb = new StringBuilder();
            sb.append("const BASE_URL = process.env.API_BASE_URL;\n\n")
                    .append("// ").append(intent).append("\n")
                    .append("test('").append(testName).append("', async () => {\n")
                    .append("  const response = await fetch(`${BASE_URL}").append(path).append("`, {\n")
                    .append("    method: '").append(method).append("',\n")
                    .append("    headers: { 'Content-Type': 'application/json' },\n");
            if (body != null) {
                sb.append("    body: ").append(escapeForJsLiteral(body)).append(",\n");
            }
            sb.append("  });\n")
                    .append("  expect([").append(codes).append("]).toContain(response.status);\n")
                    .append("});\n");
            return sb.toString();
        }
        if (framework.startsWith("PyTest")) {
            StringBuilder sb = new StringBuilder();
            sb.append("import os\nimport requests\n\n")
                    .append("BASE_URL = os.environ[\"API_BASE_URL\"]\n\n")
                    .append("# ").append(intent).append("\n")
                    .append("def ").append(testName).append("():\n")
                    .append("    response = requests.").append(method.toLowerCase(Locale.ROOT))
                    .append("(f\"{BASE_URL}").append(path).append("\"");
            if (body != null) {
                // Sent as `data=` rather than `json=` on purpose: some of these probes are deliberately
                // not valid JSON, and json= would refuse to serialize them.
                sb.append(", data=").append(escapeForPythonLiteral(body))
                        .append(", headers={\"Content-Type\": \"application/json\"}");
            }
            sb.append(", timeout=10)\n")
                    .append("    assert response.status_code in (").append(codes).append(")\n");
            return sb.toString();
        }
        // Language-agnostic fallback: a raw HTTP request any client (curl, Postman, .http file) can replay.
        return method + " " + path + "\n"
                + "Content-Type: application/json\n"
                + (body != null ? "\n" + body + "\n" : "")
                + "\n# " + intent + "\n# Pass when the response status is one of: " + expectedCodes + "\n";
    }

    private String escapeForJavaLiteral(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeForJsLiteral(String raw) {
        return "'" + raw.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String escapeForPythonLiteral(String raw) {
        return "'" + raw.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    // ─── Unit-test scaffolds from real declared units ───────────────────────────

    private List<GeneratedTestSpec> unitScaffolds(ProjectStructureSummary structure, String primaryLanguage) {
        if (structure == null || structure.keyFiles().isEmpty()) {
            return List.of();
        }
        List<GeneratedTestSpec> specs = new ArrayList<>();
        String reason = "Unit tests exercise the project's own classes and functions, so running them requires "
                + "that project's compiler, dependency graph and test runner. QPilot generates the test but does "
                + "not build or execute uploaded code, so this test is reported as generated — never as passing.";

        for (ProjectStructureSummary.KeyFile keyFile : structure.keyFiles()) {
            if (specs.size() >= MAX_UNIT_TESTS) {
                break;
            }
            String extension = extensionOf(keyFile.relativePath());
            String excerpt = keyFile.excerpt();
            if (excerpt == null || excerpt.isBlank()) {
                continue;
            }
            switch (extension) {
                case "java", "kt", "scala" -> addJvmUnitScaffolds(keyFile, excerpt, specs, reason);
                case "js", "jsx", "ts", "tsx" -> addJsUnitScaffolds(keyFile, excerpt, specs, reason);
                case "py" -> addPyUnitScaffolds(keyFile, excerpt, specs, reason);
                default -> { /* no unit-test emitter for this language yet */ }
            }
        }
        return specs;
    }

    private void addJvmUnitScaffolds(ProjectStructureSummary.KeyFile keyFile, String excerpt,
                                     List<GeneratedTestSpec> specs, String reason) {
        Matcher classMatcher = JVM_CLASS.matcher(excerpt);
        String className = classMatcher.find() ? classMatcher.group(1) : simpleName(keyFile.relativePath());

        Matcher methodMatcher = JVM_PUBLIC_METHOD.matcher(excerpt);
        int added = 0;
        while (methodMatcher.find() && added < 2 && specs.size() < MAX_UNIT_TESTS) {
            String returnType = methodMatcher.group(1).trim();
            String methodName = methodMatcher.group(2);
            String params = methodMatcher.group(3).trim();
            if (NOISE_METHOD_NAMES.contains(methodName) || methodName.equals(className)) {
                continue;
            }
            String testName = "should_return_expected_result_from_" + methodName;
            String code = "import org.junit.jupiter.api.Test;\n"
                    + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
                    + "class " + className + "Test {\n\n"
                    + "    // Target: " + className + "." + methodName + "(" + params + ") -> " + returnType + "\n"
                    + "    // Declared in " + keyFile.relativePath() + "\n"
                    + "    @Test\n"
                    + "    void " + testName + "() {\n"
                    + "        // Arrange: construct " + className + " with its collaborators (stub or mock them).\n"
                    + "        // " + className + " subject = new " + className + "(/* dependencies */);\n\n"
                    + "        // Act: invoke the real method under test.\n"
                    + "        // " + (returnType.equals("void") ? "" : "var result = ")
                    + "subject." + methodName + "(" + placeholderArgs(params) + ");\n\n"
                    + "        // Assert: state the behaviour this method is contractually required to have.\n"
                    + (returnType.equals("void")
                        ? "        // assertThat(/* observable side effect */).isTrue();\n"
                        : "        // assertThat(result).isNotNull();\n")
                    + "    }\n}\n";
            specs.add(new GeneratedTestSpec(TestType.UNIT, testName, className + "." + methodName,
                    "JUnit 5 / AssertJ",
                    "Scaffold for the real method " + className + "." + methodName + "(" + params + ") found in "
                            + keyFile.relativePath() + ". Fill in the arrange/assert steps with the behaviour this "
                            + "method must guarantee.",
                    code, false, null, null, null, null, reason));
            added++;
        }
    }

    private void addJsUnitScaffolds(ProjectStructureSummary.KeyFile keyFile, String excerpt,
                                    List<GeneratedTestSpec> specs, String reason) {
        Matcher matcher = JS_EXPORTED.matcher(excerpt);
        int added = 0;
        while (matcher.find() && added < 2 && specs.size() < MAX_UNIT_TESTS) {
            String fnName = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String params = matcher.group(1) != null ? nullToEmpty(matcher.group(2)) : nullToEmpty(matcher.group(4));
            if (fnName == null || fnName.length() < 3) {
                continue;
            }
            String importPath = "./" + keyFile.relativePath().replaceAll("\\.[jt]sx?$", "");
            String testName = "should_return_expected_result_from_" + fnName;
            String code = "import { " + fnName + " } from '" + importPath + "';\n\n"
                    + "// Target: " + fnName + "(" + params + ") exported from " + keyFile.relativePath() + "\n"
                    + "describe('" + fnName + "', () => {\n"
                    + "  it('" + testName + "', async () => {\n"
                    + "    // Arrange: build the inputs this function is documented to accept.\n"
                    + "    // Act\n"
                    + "    // const result = await " + fnName + "(" + placeholderArgs(params) + ");\n"
                    + "    // Assert\n"
                    + "    // expect(result).toBeDefined();\n"
                    + "  });\n});\n";
            specs.add(new GeneratedTestSpec(TestType.UNIT, testName, fnName, "Jest / Vitest",
                    "Scaffold for the real exported function " + fnName + "(" + params + ") found in "
                            + keyFile.relativePath() + ".",
                    code, false, null, null, null, null, reason));
            added++;
        }
    }

    private void addPyUnitScaffolds(ProjectStructureSummary.KeyFile keyFile, String excerpt,
                                    List<GeneratedTestSpec> specs, String reason) {
        Matcher matcher = PY_DEF.matcher(excerpt);
        int added = 0;
        while (matcher.find() && added < 2 && specs.size() < MAX_UNIT_TESTS) {
            String fnName = matcher.group(1);
            String params = nullToEmpty(matcher.group(2));
            if (fnName.length() < 3) {
                continue;
            }
            String module = keyFile.relativePath().replaceAll("\\.py$", "").replace('/', '.');
            String testName = "test_" + fnName + "_returns_expected_result";
            String code = "from " + module + " import " + fnName + "\n\n"
                    + "# Target: " + fnName + "(" + params + ") declared in " + keyFile.relativePath() + "\n"
                    + "def " + testName + "():\n"
                    + "    # Arrange: build the inputs this function is documented to accept.\n"
                    + "    # Act\n"
                    + "    # result = " + fnName + "(" + placeholderArgs(params) + ")\n"
                    + "    # Assert\n"
                    + "    # assert result is not None\n"
                    + "    pass\n";
            specs.add(new GeneratedTestSpec(TestType.UNIT, testName, fnName, "PyTest",
                    "Scaffold for the real function " + fnName + "(" + params + ") found in "
                            + keyFile.relativePath() + ".",
                    code, false, null, null, null, null, reason));
            added++;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String placeholderArgs(String params) {
        if (params == null || params.isBlank()) {
            return "";
        }
        int count = params.split(",").length;
        return String.join(", ", Collections.nCopies(count, "/* arg */"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String simpleName(String relativePath) {
        String name = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private String extensionOf(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        return dot == -1 ? "" : relativePath.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String pascal(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
