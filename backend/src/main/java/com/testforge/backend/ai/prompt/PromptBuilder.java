package com.testforge.backend.ai.prompt;

import com.testforge.backend.ai.dto.AgentType;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerEndpointSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the system/user prompts fed to each AI agent. Rather than shipping
 * the whole repository to the LLM (which would blow past context limits),
 * this compresses the statically-analyzed {@link ProjectStructureSummary}
 * (+ optional Swagger spec) into a compact text block: language mix,
 * dependencies, discovered endpoints, and a handful of key file excerpts.
 * This is the lightweight stand-in for a full RAG/vector-DB pipeline
 * (explicitly out of scope for the hackathon MVP — see PROJECT_PLAN.md).
 */
@Component
public class PromptBuilder {

    private static final int MAX_ENDPOINTS_LISTED = 40;

    public String buildProjectContext(ProjectStructureSummary structure, SwaggerParseResult swagger) {
        StringBuilder sb = new StringBuilder();

        sb.append("Primary language: ").append(structure.primaryLanguage()).append('\n');
        sb.append("Language breakdown: ").append(formatMap(structure.languageBreakdown())).append('\n');
        sb.append("Total files analyzed: ").append(structure.totalFiles()).append('\n');
        sb.append("Top-level structure: ").append(String.join(", ", structure.topLevelEntries())).append('\n');

        if (!structure.dependencies().isEmpty()) {
            sb.append("Dependencies: ")
                    .append(structure.dependencies().stream().limit(60).collect(Collectors.joining(", ")))
                    .append('\n');
        }

        sb.append("\nDiscovered API endpoints (from static source scan):\n");
        if (structure.endpoints().isEmpty()) {
            sb.append("(none detected)\n");
        } else {
            for (ApiEndpointSummary ep : structure.endpoints().stream().limit(MAX_ENDPOINTS_LISTED).toList()) {
                sb.append("- ").append(ep.httpMethod()).append(' ').append(ep.path())
                        .append(" (").append(ep.sourceFile()).append(")\n");
            }
        }

        if (swagger != null && !swagger.endpoints().isEmpty()) {
            sb.append("\nUploaded Swagger/OpenAPI spec \"").append(swagger.title()).append("\" v")
                    .append(swagger.version()).append(" endpoints:\n");
            for (SwaggerEndpointSummary ep : swagger.endpoints().stream().limit(MAX_ENDPOINTS_LISTED).toList()) {
                sb.append("- ").append(ep.httpMethod()).append(' ').append(ep.path());
                if (ep.summary() != null && !ep.summary().isBlank()) {
                    sb.append(" — ").append(ep.summary());
                }
                sb.append('\n');
            }
        }

        if (!structure.keyFiles().isEmpty()) {
            sb.append("\nKey source file excerpts:\n");
            for (ProjectStructureSummary.KeyFile kf : structure.keyFiles()) {
                sb.append("\n--- ").append(kf.relativePath()).append(" ---\n").append(kf.excerpt()).append('\n');
            }
        }

        return sb.toString();
    }

    public AiPrompt codeSummaryPrompt(String projectName, String projectContext) {
        String system = "You are the Code Understanding agent inside AI TestPilot, an AI QA engineer. "
                + "Read the supplied project context and explain, in plain English, what the project does, "
                + "its key responsibilities, and anything unusual worth flagging. Be concrete and reference "
                + "real file/endpoint names from the context when possible. Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName + "\n\n" + projectContext;
        return new AiPrompt(AgentType.CODE_SUMMARY, system, user, JsonSchemas.CODE_SUMMARY);
    }

    public AiPrompt testGenerationPrompt(String projectName, String projectContext, String codeSummaryJson) {
        String system = "You are the Test Generation agent inside AI TestPilot. Given a project's structure, "
                + "discovered API endpoints, and code summary, generate a comprehensive, realistic test suite: "
                + "unit tests for individual methods, API tests per endpoint (cover positive cases, wrong/invalid "
                + "input, SQL injection, missing/expired JWT, rate limiting, concurrent access, invalid JSON, "
                + "and other negative cases), integration tests for multi-step flows you can infer (e.g. "
                + "register -> login -> create -> pay -> verify), edge cases (null, empty, huge input, unicode, "
                + "negative numbers, overflow, duplicate requests, slow network, wrong headers, huge payload), and "
                + "security-flavored tests. Generate compilable-looking, idiomatic code for the project's primary "
                + "language/framework (JUnit5/Mockito for Java+Spring, RestAssured/Postman-style for pure API specs, "
                + "Jest/Supertest for Node, PyTest for Python). Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName + "\n\nCode summary from the Code Understanding agent:\n"
                + codeSummaryJson + "\n\nProject context:\n" + projectContext;
        return new AiPrompt(AgentType.TEST_GENERATION, system, user, JsonSchemas.TEST_GENERATION);
    }

    public AiPrompt securityAnalysisPrompt(String projectName, String projectContext, String codeSummaryJson) {
        String system = "You are the Security agent inside AI TestPilot. Inspect the project context for security "
                + "weaknesses across the OWASP-style checklist: SQL injection, XSS, CSRF, broken authentication, "
                + "JWT handling issues, privilege escalation, IDOR, sensitive data exposure, and missing input "
                + "validation. Only report findings you can plausibly justify from the given context (endpoints, "
                + "dependencies, file excerpts) — cite the relevant endpoint/file when you can. Respond ONLY with "
                + "JSON matching the schema.";
        String user = "Project name: " + projectName + "\n\nCode summary:\n" + codeSummaryJson
                + "\n\nProject context:\n" + projectContext;
        return new AiPrompt(AgentType.SECURITY_ANALYSIS, system, user, JsonSchemas.SECURITY_ANALYSIS);
    }

    public AiPrompt riskScorePrompt(String projectName, String projectContext, String codeSummaryJson,
                                     int generatedTestCount, int securityFindingCount) {
        String system = "You are the Coverage & Risk agent inside AI TestPilot, the final step of the pipeline. "
                + "Combine the code summary, how many tests/security findings were already generated, and the "
                + "project context to produce an overall risk score (0-100, higher = riskier) with concrete reasons "
                + "(e.g. authentication changed, payment logic present, missing validation, critical endpoints "
                + "untested), plus an estimated test-coverage percentage and a list of concrete coverage gaps "
                + "(modules/endpoints/classes that likely lack tests). Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName
                + "\nGenerated tests so far: " + generatedTestCount
                + "\nSecurity findings so far: " + securityFindingCount
                + "\n\nCode summary:\n" + codeSummaryJson
                + "\n\nProject context:\n" + projectContext;
        return new AiPrompt(AgentType.RISK_SCORE, system, user, JsonSchemas.RISK_SCORE);
    }

    private String formatMap(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
