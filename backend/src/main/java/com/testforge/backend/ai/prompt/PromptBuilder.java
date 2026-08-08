package com.testforge.backend.ai.prompt;

import com.testforge.backend.ai.dto.AgentType;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.analysis.scan.ScanFinding;
import com.testforge.backend.analysis.scan.StaticScanResult;
import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerEndpointSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the prompts fed to each AI agent.
 *
 * <p>Two principles shape every prompt here. First, context is compressed rather than dumped: the
 * statically-analyzed {@link ProjectStructureSummary} (plus any OpenAPI document) is distilled into a
 * compact block — language mix, dependencies, discovered routes, a handful of key file excerpts —
 * because shipping a whole repository would blow past context limits and bury the signal.
 *
 * <p>Second, and more importantly, the model is never asked to produce a measurement. QPilot has
 * already counted the files, matched the security rules and computed the risk score deterministically;
 * those numbers are handed to the model as ground truth, and it is asked to interpret, prioritize and
 * fill the gaps that pattern matching cannot reach. Each prompt states plainly what has already been
 * found so the model adds to it instead of restating it in less reliable form.
 */
@Component
public class PromptBuilder {

    private static final int MAX_ENDPOINTS_LISTED = 40;
    private static final int MAX_FINDINGS_LISTED = 25;
    private static final int MAX_TESTS_LISTED = 30;

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
            sb.append("\nOpenAPI spec \"").append(swagger.title()).append("\" v")
                    .append(swagger.version()).append(" endpoints:\n");
            for (SwaggerEndpointSummary ep : swagger.endpoints().stream().limit(MAX_ENDPOINTS_LISTED).toList()) {
                sb.append("- ").append(ep.httpMethod()).append(' ').append(ep.path());
                if (ep.summary() != null && !ep.summary().isBlank()) {
                    sb.append(" - ").append(ep.summary());
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

    /**
     * Renders the static engine's measured output as prompt input. Handing the model the real counts
     * keeps its prose anchored to the same numbers the UI displays, instead of producing a second,
     * conflicting account of the project's state.
     */
    public String buildMeasuredFacts(StaticScanResult scan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Files scanned: ").append(scan.metrics().filesScanned())
                .append(" (").append(scan.metrics().sourceFileCount()).append(" source, ")
                .append(scan.metrics().testFileCount()).append(" test)\n");
        sb.append("Non-blank lines of code: ").append(scan.metrics().nonBlankLinesOfCode()).append('\n');
        sb.append("HTTP endpoints discovered: ").append(scan.metrics().endpointCount())
                .append(", of which referenced by tests: ").append(scan.metrics().endpointsReferencedByTests())
                .append('\n');
        sb.append("Tested surface: ").append(scan.testedSurfacePercent()).append("% (")
                .append(scan.testedSurfaceBasis()).append(")\n");
        sb.append("Computed risk score: ").append(scan.riskScore()).append("/100\n");
        sb.append("Risk score breakdown: ").append(String.join(" | ", scan.scoreBreakdown())).append('\n');

        if (!scan.metrics().testFrameworksDetected().isEmpty()) {
            sb.append("Test frameworks detected: ")
                    .append(String.join(", ", scan.metrics().testFrameworksDetected())).append('\n');
        }
        if (!scan.findings().isEmpty()) {
            sb.append("\nSecurity findings with file/line evidence:\n").append(formatFindings(scan.findings()));
        } else {
            sb.append("\nSecurity findings: none of the configured patterns matched.\n");
        }
        if (!scan.coverageGaps().isEmpty()) {
            sb.append("\nUntested endpoints/areas:\n");
            scan.coverageGaps().stream().limit(MAX_FINDINGS_LISTED)
                    .forEach(gap -> sb.append("- ").append(gap).append('\n'));
        }
        sb.append("\nChecks that could NOT be performed (do not claim results for these):\n");
        scan.metrics().unavailableChecks().forEach(note -> sb.append("- ").append(note).append('\n'));
        return sb.toString();
    }

    public String formatFindings(List<ScanFinding> findings) {
        return findings.stream()
                .limit(MAX_FINDINGS_LISTED)
                .map(f -> "- [" + f.severity() + "] " + f.category() + " at " + f.filePath() + ":" + f.lineNumber())
                .collect(Collectors.joining("\n")) + "\n";
    }

    public String formatGeneratedTestTitles(List<String> titles) {
        if (titles.isEmpty()) {
            return "(none - no routes were discovered, so no per-endpoint tests could be generated)\n";
        }
        return titles.stream().limit(MAX_TESTS_LISTED).map(t -> "- " + t).collect(Collectors.joining("\n")) + "\n";
    }

    // ─── Agent prompts ───────────────────────────────────────────────────────────

    public AiPrompt codeSummaryPrompt(String projectName, String projectContext) {
        String system = "You are the Code Understanding agent inside QPilot AI. Read the supplied project context "
                + "and explain, in plain English, what the project does, its key responsibilities, and anything "
                + "unusual worth flagging. Be concrete: reference real file, class and endpoint names that appear "
                + "in the context. Do not speculate about code you were not shown, and do not state metrics or "
                + "counts - a separate analyzer owns those. Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName + "\n\n" + projectContext;
        return new AiPrompt(AgentType.CODE_SUMMARY, system, user, JsonSchemas.CODE_SUMMARY);
    }

    /**
     * Asks for scenarios QPilot's route scanner cannot derive on its own — multi-step business flows,
     * domain-specific edge cases — rather than duplicating the per-endpoint tests already generated
     * deterministically. Everything returned is stored as an AI suggestion and never marked as passing.
     */
    public AiPrompt testGenerationPrompt(String projectName, String projectContext, String alreadyGenerated) {
        String system = "You are the Test Ideation agent inside QPilot AI. QPilot has ALREADY generated "
                + "per-endpoint API tests deterministically from the project's discovered routes, and they are "
                + "listed below. Do not repeat them. Your job is to add the cases static route scanning cannot "
                + "infer: multi-step integration flows spanning several endpoints (e.g. register -> login -> "
                + "create -> verify), domain-specific business-rule violations, concurrency and idempotency "
                + "hazards, and boundary conditions specific to this project's data model. Ground every test in a "
                + "real endpoint, class or function name taken from the supplied context - never invent a class or "
                + "path that does not appear there. Write idiomatic, compilable-looking code for the project's own "
                + "language and test framework. Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName
                + "\n\nTests QPilot already generated deterministically (do NOT duplicate these):\n" + alreadyGenerated
                + "\n\nProject context:\n" + projectContext;
        return new AiPrompt(AgentType.TEST_GENERATION, system, user, JsonSchemas.TEST_GENERATION);
    }

    /**
     * Asks for weaknesses pattern matching structurally cannot see — missing authorization checks,
     * broken access control, unsafe trust boundaries. Results are persisted with AI_SUGGESTION
     * provenance so the UI never presents them as scanned evidence.
     */
    public AiPrompt securityAnalysisPrompt(String projectName, String projectContext, String staticFindings) {
        String system = "You are the Security Review agent inside QPilot AI. A deterministic pattern scanner has "
                + "already flagged the issues listed below with file and line evidence - do not repeat them. Your "
                + "job is to identify weaknesses a line-level pattern scan structurally cannot detect: missing "
                + "authorization checks on endpoints that clearly handle other users' data, broken access control "
                + "and IDOR, trust-boundary mistakes, unsafe defaults implied by the architecture, and flawed "
                + "authentication or session logic. Report only what you can justify from the supplied endpoints, "
                + "dependencies and file excerpts, and cite the specific endpoint or file for each finding. If the "
                + "context is too limited to support a finding, return fewer findings rather than speculating - "
                + "your output is labelled as an unverified suggestion for human review, so precision matters more "
                + "than volume. Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName
                + "\n\nIssues the static scanner already found (do NOT duplicate):\n" + staticFindings
                + "\n\nProject context:\n" + projectContext;
        return new AiPrompt(AgentType.SECURITY_ANALYSIS, system, user, JsonSchemas.SECURITY_ANALYSIS);
    }

    /**
     * Asks the model to interpret and prioritize, given measurements QPilot already made.
     *
     * <p>The measured facts are supplied as input and the model is explicitly instructed not to invent
     * competing numbers. This is the division of labour the whole pipeline is built around: counting is
     * the scanner's job because it can be verified, and explaining is the model's job because prose
     * judgement is what it is actually good at.
     */
    public AiPrompt recommendationsPrompt(String projectName, String projectContext, String measuredFacts) {
        String system = "You are the Quality Advisor agent inside QPilot AI. You are given measurements already "
                + "computed by a deterministic static analyzer over the project's real files, plus the project "
                + "context. Your job is to interpret and prioritize, NOT to measure. Rules you must follow: "
                + "(1) Do not invent or restate numeric metrics - no risk scores, no coverage percentages, no "
                + "counts other than the ones supplied to you. (2) Ground every recommendation in a specific "
                + "supplied measurement, endpoint, file or dependency, and say which one. (3) If the measurements "
                + "are insufficient to justify a recommendation, say so instead of speculating. (4) Prefer a few "
                + "high-value actions over a long generic checklist. Respond ONLY with JSON matching the schema.";
        String user = "Project name: " + projectName
                + "\n\nMEASURED FACTS (computed by static analysis - treat as ground truth):\n" + measuredFacts
                + "\n\nPROJECT CONTEXT:\n" + projectContext;
        return new AiPrompt(AgentType.RECOMMENDATIONS, system, user, JsonSchemas.RECOMMENDATIONS);
    }

    private String formatMap(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
