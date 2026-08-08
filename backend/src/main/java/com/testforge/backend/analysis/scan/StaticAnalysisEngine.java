package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.Severity;
import com.testforge.backend.common.util.IgnoredPaths;
import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Deterministic, reproducible analysis of a project's real files.
 *
 * <p>This is QPilot's factual layer. Everything it returns is either a count of something that
 * exists on disk or a rule match with a file, a line number and the matched text attached — which
 * means two runs over the same archive produce the same answer, and every number can be checked by
 * opening the cited file. That property is the whole reason this class exists: the results it feeds
 * into the dashboard, the risk score and the reports are measurements, not impressions.
 *
 * <p>It runs unconditionally, whether or not an AI provider is configured. AI enrichment (see
 * {@code AnalysisService}) is layered on top and is always attributed separately, so an unconfigured
 * or failing AI provider degrades the product to "fewer narrative insights" rather than to
 * "invented findings".
 *
 * <p>Scope is honest about its limits: these are lexical pattern checks, not interprocedural dataflow
 * analysis, and no dependency-advisory database ships with the app. Checks that cannot run are
 * reported as unavailable rather than skipped silently.
 */
@Service
public class StaticAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisEngine.class);

    /** Extensions treated as application source for counting and rule application. */
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "scala", "js", "jsx", "ts", "tsx", "vue", "svelte", "py", "go", "rb",
            "php", "cs", "cpp", "c", "h", "rs", "dart", "swift", "sql", "yml", "yaml", "properties",
            "json", "xml", "env", "sh", "tf", "html");

    /** A file is a test if its path or name matches how test files are conventionally named. */
    private static final Pattern TEST_PATH = Pattern.compile(
            "(?i)(^|/)(src/test|test|tests|spec|specs|__tests__|e2e|cypress|playwright)(/|$)");
    private static final Pattern TEST_FILE_NAME = Pattern.compile(
            "(?i).*([._-](test|tests|spec)s?|^test_.*|.*Test|.*Tests|.*Spec|.*IT)\\.[a-z]+$");

    private static final Pattern BLANK_OR_COMMENT = Pattern.compile("^\\s*(//|#|/\\*|\\*|--)?\\s*$");

    /** Public/exported declarations, used to name real units for unit-test scaffolds. */
    private static final Pattern JVM_METHOD = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?[\\w<>,\\[\\]\\s.?]+\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_EXPORTED_FN = Pattern.compile(
            "export\\s+(?:default\\s+)?(?:async\\s+)?function\\s+(\\w+)|"
                    + "export\\s+const\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\(");
    private static final Pattern PY_DEF = Pattern.compile("^\\s*def\\s+(?!_)(\\w+)\\s*\\(", Pattern.MULTILINE);

    private static final int MAX_FILES_TO_SCAN = 20_000;
    private static final long MAX_FILE_BYTES_TO_READ = 1_500_000; // skip minified bundles / huge blobs
    private static final int MAX_FINDINGS_PER_RULE = 5;
    private static final int MAX_TOTAL_FINDINGS = 120;
    private static final int MAX_EVIDENCE_CHARS = 300;
    private static final int MAX_COVERAGE_GAPS = 25;

    /** Risk weights per severity. Fixed and documented so the score is explainable, not magic. */
    private static final int WEIGHT_CRITICAL = 15;
    private static final int WEIGHT_HIGH = 8;
    private static final int WEIGHT_MEDIUM = 3;
    private static final int WEIGHT_LOW = 1;
    private static final int MAX_SECURITY_POINTS = 60;
    private static final int MAX_UNTESTED_POINTS = 30;
    private static final int NO_TESTS_AT_ALL_PENALTY = 10;

    /**
     * Scans an extracted project directory.
     *
     * @param projectRoot root of the extracted source tree
     * @param structure   previously-computed structure summary (languages, endpoints, dependencies)
     * @param projectName project name, used only for narrative text
     */
    public StaticScanResult scan(Path projectRoot, ProjectStructureSummary structure, String projectName) {
        List<Path> files = collectFiles(projectRoot);

        List<Path> sourceFiles = new ArrayList<>();
        List<Path> testFiles = new ArrayList<>();
        long totalLines = 0;
        long nonBlankLines = 0;

        Map<String, List<ScanFinding>> findingsByRule = new LinkedHashMap<>();
        StringBuilder testCorpus = new StringBuilder();
        Set<String> declaredUnits = new LinkedHashSet<>();

        for (Path file : files) {
            String relative = relativize(projectRoot, file);
            String extension = extensionOf(file);
            boolean isTest = isTestFile(relative);

            if (!SOURCE_EXTENSIONS.contains(extension)) {
                continue;
            }

            String content = readTextOrNull(file);
            if (content == null) {
                continue;
            }

            String[] lines = content.split("\n", -1);
            if (isTest) {
                testFiles.add(file);
                // Accumulate test text so endpoint-coverage can be measured by literal path matching.
                testCorpus.append(content).append('\n');
            } else {
                sourceFiles.add(file);
                totalLines += lines.length;
                for (String line : lines) {
                    if (!BLANK_OR_COMMENT.matcher(line).matches()) {
                        nonBlankLines++;
                    }
                }
                collectDeclaredUnits(extension, content, relative, declaredUnits);
            }

            // Security rules run against production code only: a hardcoded password inside a fixture
            // is a different (and usually acceptable) thing from one inside a request handler, and
            // conflating them buries the findings that matter under test-data noise.
            if (!isTest) {
                applyRules(relative, extension, lines, findingsByRule);
            }
        }

        List<ScanFinding> findings = flattenFindings(findingsByRule);

        // ─── Endpoint coverage: measured by looking for each real route path in test sources ───
        List<ApiEndpointSummary> endpoints = structure != null ? structure.endpoints() : List.of();
        String tests = testCorpus.toString();
        List<String> untestedEndpoints = new ArrayList<>();
        int referenced = 0;
        for (ApiEndpointSummary endpoint : endpoints) {
            if (isEndpointReferenced(endpoint, tests)) {
                referenced++;
            } else if (untestedEndpoints.size() < MAX_COVERAGE_GAPS) {
                untestedEndpoints.add(endpoint.httpMethod() + " " + endpoint.path()
                        + "  (" + endpoint.sourceFile() + ")");
            }
        }

        TestedSurface surface = computeTestedSurface(endpoints.size(), referenced,
                sourceFiles.size(), testFiles.size());

        List<String> testFrameworks = detectTestFrameworks(structure, tests);
        List<String> unavailable = buildUnavailableChecks(structure, testFiles.size());

        ScanMetrics metrics = new ScanMetrics(
                files.size(), sourceFiles.size(), testFiles.size(), totalLines, nonBlankLines,
                endpoints.size(), referenced,
                structure != null ? structure.dependencies().size() : 0,
                structure != null ? structure.languageBreakdown() : Map.of(),
                testFrameworks, unavailable);

        RiskComputation risk = computeRisk(findings, surface, testFiles.size(), sourceFiles.size());

        List<String> gaps = new ArrayList<>(untestedEndpoints);
        if (testFiles.isEmpty() && sourceFiles.size() > 0) {
            gaps.add(0, "No test files were found anywhere in the project (" + sourceFiles.size()
                    + " source files scanned).");
        }

        return new StaticScanResult(
                metrics, findings, risk.score(), risk.reasons(), risk.breakdown(),
                surface.percent(), surface.basis(), gaps,
                buildSummary(projectName, metrics, findings, surface, structure),
                buildObservations(metrics, findings, declaredUnits, structure));
    }

    // ─── File collection & classification ────────────────────────────────────────

    private List<Path> collectFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !IgnoredPaths.containsIgnoredSegment(relativize(root, p)))
                    .limit(MAX_FILES_TO_SCAN)
                    .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Could not walk project directory " + root + ": " + e.getMessage(), e);
        }
        return files;
    }

    private boolean isTestFile(String relativePath) {
        String fileName = relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                : relativePath;
        return TEST_PATH.matcher(relativePath).find() || TEST_FILE_NAME.matcher(fileName).matches();
    }

    private String readTextOrNull(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES_TO_READ) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return null; // binary or non-UTF-8 file: nothing meaningful to scan
        } catch (IOException e) {
            log.debug("Skipping unreadable file {}: {}", file, e.getMessage());
            return null;
        }
    }

    // ─── Rule application ────────────────────────────────────────────────────────

    private void applyRules(String relativePath, String extension, String[] lines,
                            Map<String, List<ScanFinding>> findingsByRule) {
        for (SecurityRule rule : SecurityRules.ALL) {
            if (!rule.appliesTo(extension)) {
                continue;
            }
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.isBlank() || line.length() > 2000) {
                    continue; // minified/generated lines produce noise, not signal
                }
                Matcher matcher = rule.pattern().matcher(line);
                if (!matcher.find() || rule.isSuppressed(line)) {
                    continue;
                }
                if (!rule.contextSatisfied(lines, i)) {
                    continue;
                }
                List<ScanFinding> forRule = findingsByRule.computeIfAbsent(rule.id(), k -> new ArrayList<>());
                // Keep counting past the display cap so the reported occurrence total stays truthful.
                if (forRule.size() < MAX_FINDINGS_PER_RULE) {
                    forRule.add(new ScanFinding(
                            rule.id(), rule.category(), rule.severity(),
                            rule.description(), rule.recommendation(),
                            relativePath, i + 1, truncate(line.trim()), 1));
                } else {
                    ScanFinding first = forRule.get(0);
                    forRule.set(0, new ScanFinding(first.ruleId(), first.category(), first.severity(),
                            first.description(), first.recommendation(), first.filePath(), first.lineNumber(),
                            first.evidence(), first.occurrenceCount() + 1));
                }
                break; // one finding per rule per line is enough
            }
        }
    }

    private List<ScanFinding> flattenFindings(Map<String, List<ScanFinding>> findingsByRule) {
        List<ScanFinding> all = new ArrayList<>();
        for (List<ScanFinding> perRule : findingsByRule.values()) {
            int extra = perRule.get(0).occurrenceCount() - 1;
            int total = perRule.size() + Math.max(0, extra);
            for (ScanFinding f : perRule) {
                all.add(new ScanFinding(f.ruleId(), f.category(), f.severity(), f.description(),
                        f.recommendation(), f.filePath(), f.lineNumber(), f.evidence(), total));
            }
        }
        all.sort(Comparator.comparingInt((ScanFinding f) -> severityRank(f.severity()))
                .thenComparing(ScanFinding::filePath));
        return all.size() > MAX_TOTAL_FINDINGS ? new ArrayList<>(all.subList(0, MAX_TOTAL_FINDINGS)) : all;
    }

    private int severityRank(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    // ─── Coverage measurement ────────────────────────────────────────────────────

    /**
     * True when a route's literal path appears anywhere in the project's test sources. This is a
     * conservative textual signal, not proof the endpoint is well tested — hence the deliberately
     * named "referenced by tests" rather than "covered".
     */
    private boolean isEndpointReferenced(ApiEndpointSummary endpoint, String testCorpus) {
        if (testCorpus.isEmpty()) {
            return false;
        }
        String path = endpoint.path();
        if (path == null || path.isBlank() || path.equals("/")) {
            return false;
        }
        if (testCorpus.contains(path)) {
            return true;
        }
        // Path templates differ between declaration and test ("/users/{id}" vs "/users/42"), so also
        // try the static prefix ahead of the first variable segment.
        int brace = path.indexOf('{');
        int colon = path.indexOf(':');
        int cut = brace >= 0 ? brace : colon;
        if (cut > 1) {
            String prefix = path.substring(0, cut);
            return prefix.length() > 4 && testCorpus.contains(prefix);
        }
        return false;
    }

    private record TestedSurface(int percent, String basis) {
    }

    private TestedSurface computeTestedSurface(int endpointCount, int referencedCount,
                                               int sourceFileCount, int testFileCount) {
        if (endpointCount > 0) {
            int percent = (int) Math.round(referencedCount * 100.0 / endpointCount);
            return new TestedSurface(percent, referencedCount + " of " + endpointCount
                    + " discovered HTTP endpoints have their route path referenced in a test file. "
                    + "This measures endpoint reachability from tests, not executed line coverage.");
        }
        if (sourceFileCount > 0) {
            // No routes discovered (library, frontend-only or unsupported framework): fall back to a
            // file-ratio signal, and say so plainly rather than presenting it as endpoint coverage.
            int percent = (int) Math.min(100, Math.round(testFileCount * 100.0 / sourceFileCount));
            return new TestedSurface(percent, "No HTTP endpoints were discovered, so this is the ratio of "
                    + testFileCount + " test files to " + sourceFileCount + " source files — a structural "
                    + "signal only, not executed line coverage.");
        }
        return new TestedSurface(0, "No source files were available to measure.");
    }

    private List<String> detectTestFrameworks(ProjectStructureSummary structure, String testCorpus) {
        Set<String> found = new LinkedHashSet<>();
        List<String> dependencies = structure != null ? structure.dependencies() : List.of();
        Map<String, String> markers = new LinkedHashMap<>();
        markers.put("junit", "JUnit");
        markers.put("mockito", "Mockito");
        markers.put("testng", "TestNG");
        markers.put("rest-assured", "REST Assured");
        markers.put("jest", "Jest");
        markers.put("vitest", "Vitest");
        markers.put("mocha", "Mocha");
        markers.put("cypress", "Cypress");
        markers.put("playwright", "Playwright");
        markers.put("pytest", "PyTest");
        markers.put("unittest", "unittest");
        markers.put("testify", "Testify");
        markers.put("rspec", "RSpec");
        markers.put("phpunit", "PHPUnit");

        for (String dependency : dependencies) {
            String lower = dependency.toLowerCase(Locale.ROOT);
            markers.forEach((marker, label) -> {
                if (lower.contains(marker)) {
                    found.add(label);
                }
            });
        }
        String lowerTests = testCorpus.toLowerCase(Locale.ROOT);
        markers.forEach((marker, label) -> {
            if (lowerTests.contains(marker)) {
                found.add(label);
            }
        });
        return new ArrayList<>(found);
    }

    /**
     * Checks QPilot genuinely cannot perform for an uploaded archive. Naming them explicitly is the
     * point: an absent section reads as "clean", whereas "not available, because X" is accurate.
     */
    private List<String> buildUnavailableChecks(ProjectStructureSummary structure, int testFileCount) {
        List<String> notes = new ArrayList<>();
        notes.add("Executed line/branch coverage — not available: measuring it requires running the "
                + "project's own test suite under a coverage agent, which QPilot does not do for uploaded archives.");
        notes.add("Dependency vulnerability (CVE) matching — not available: no offline advisory database is "
                + "bundled. Run your ecosystem's auditor (npm audit, mvn dependency-check, pip-audit) for this.");
        if (structure != null && structure.endpoints().isEmpty()) {
            notes.add("HTTP endpoint discovery — no routes detected: the project may not expose HTTP routes, or "
                    + "may use a framework whose routing syntax is not among the supported patterns "
                    + "(Spring MVC, Express, Flask/FastAPI).");
        }
        if (testFileCount == 0) {
            notes.add("Test-suite quality assessment — not available: no test files were found to assess.");
        }
        return notes;
    }

    // ─── Risk scoring ────────────────────────────────────────────────────────────

    private record RiskComputation(int score, List<String> reasons, List<String> breakdown) {
    }

    /**
     * Computes the risk score with a fixed, published formula. The breakdown returned alongside it is
     * not decoration: it is what lets a reviewer disagree with the number for a specific reason
     * instead of dismissing it as arbitrary.
     */
    private RiskComputation computeRisk(List<ScanFinding> findings, TestedSurface surface,
                                        int testFileCount, int sourceFileCount) {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        for (ScanFinding finding : findings) {
            counts.merge(finding.severity(), 1, Integer::sum);
        }

        List<String> breakdown = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        int securityPoints = counts.get(Severity.CRITICAL) * WEIGHT_CRITICAL
                + counts.get(Severity.HIGH) * WEIGHT_HIGH
                + counts.get(Severity.MEDIUM) * WEIGHT_MEDIUM
                + counts.get(Severity.LOW) * WEIGHT_LOW;
        int cappedSecurity = Math.min(MAX_SECURITY_POINTS, securityPoints);

        for (Severity severity : List.of(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW)) {
            int count = counts.get(severity);
            if (count > 0) {
                int weight = weightOf(severity);
                breakdown.add(count + " " + severity.name() + " finding" + (count == 1 ? "" : "s")
                        + " x " + weight + " = +" + (count * weight));
            }
        }
        if (securityPoints > MAX_SECURITY_POINTS) {
            breakdown.add("Security subtotal capped at " + MAX_SECURITY_POINTS
                    + " (raw " + securityPoints + ") so findings alone cannot saturate the score");
        }

        int untestedPercent = Math.max(0, 100 - surface.percent());
        int untestedPoints = (int) Math.round(untestedPercent / 100.0 * MAX_UNTESTED_POINTS);
        if (untestedPoints > 0) {
            breakdown.add(untestedPercent + "% of the measured test surface untested x "
                    + MAX_UNTESTED_POINTS + " pts = +" + untestedPoints);
        }

        int noTestsPenalty = (testFileCount == 0 && sourceFileCount > 0) ? NO_TESTS_AT_ALL_PENALTY : 0;
        if (noTestsPenalty > 0) {
            breakdown.add("No test files present anywhere = +" + noTestsPenalty);
        }

        int score = Math.min(100, cappedSecurity + untestedPoints + noTestsPenalty);
        breakdown.add("Total risk score = " + score + " / 100");

        if (counts.get(Severity.CRITICAL) > 0) {
            reasons.add(counts.get(Severity.CRITICAL) + " critical finding(s) with cited file/line evidence — "
                    + "credential or key material exposure dominates the score.");
        }
        if (counts.get(Severity.HIGH) > 0) {
            reasons.add(counts.get(Severity.HIGH) + " high-severity finding(s) detected, typically injection "
                    + "or transport-security issues.");
        }
        if (testFileCount == 0 && sourceFileCount > 0) {
            reasons.add("No test files exist in the project, so no regression safety net was measurable.");
        } else if (untestedPercent >= 50) {
            reasons.add(untestedPercent + "% of the measured test surface has no corresponding tests.");
        }
        if (reasons.isEmpty()) {
            reasons.add("No critical or high-severity patterns matched, and the majority of the measured test "
                    + "surface is referenced by tests.");
        }

        return new RiskComputation(score, reasons, breakdown);
    }

    private int weightOf(Severity severity) {
        return switch (severity) {
            case CRITICAL -> WEIGHT_CRITICAL;
            case HIGH -> WEIGHT_HIGH;
            case MEDIUM -> WEIGHT_MEDIUM;
            case LOW -> WEIGHT_LOW;
        };
    }

    // ─── Narrative built strictly from measured values ───────────────────────────

    private String buildSummary(String projectName, ScanMetrics metrics, List<ScanFinding> findings,
                                TestedSurface surface, ProjectStructureSummary structure) {
        StringBuilder sb = new StringBuilder();
        sb.append("Static scan of \"").append(projectName).append("\" inspected ")
                .append(metrics.filesScanned()).append(" files (")
                .append(metrics.sourceFileCount()).append(" source, ")
                .append(metrics.testFileCount()).append(" test) totalling ")
                .append(metrics.nonBlankLinesOfCode()).append(" non-blank lines of code");
        if (structure != null && structure.primaryLanguage() != null) {
            sb.append(", predominantly ").append(structure.primaryLanguage());
        }
        sb.append(". ");

        if (metrics.endpointCount() > 0) {
            sb.append(metrics.endpointCount()).append(" HTTP endpoint(s) were discovered by route scanning, of which ")
                    .append(metrics.endpointsReferencedByTests()).append(" are referenced from test sources. ");
        } else {
            sb.append("No HTTP endpoints were discovered by route scanning. ");
        }

        if (findings.isEmpty()) {
            sb.append("No configured security pattern matched the scanned source.");
        } else {
            sb.append(findings.size()).append(" finding(s) matched, each carrying the file and line where it was ")
                    .append("observed. ");
            if (!metrics.testFrameworksDetected().isEmpty()) {
                sb.append("Test tooling detected: ").append(String.join(", ", metrics.testFrameworksDetected()))
                        .append('.');
            }
        }
        return sb.toString();
    }

    private List<String> buildObservations(ScanMetrics metrics, List<ScanFinding> findings,
                                           Set<String> declaredUnits, ProjectStructureSummary structure) {
        List<String> observations = new ArrayList<>();

        if (metrics.sourceFileCount() > 0) {
            long avgLines = metrics.nonBlankLinesOfCode() / Math.max(1, metrics.sourceFileCount());
            observations.add("Average source file size: " + avgLines + " non-blank lines across "
                    + metrics.sourceFileCount() + " files.");
        }
        if (metrics.testFileCount() > 0 && metrics.sourceFileCount() > 0) {
            observations.add("Test-to-source file ratio: 1 test file per "
                    + String.format(Locale.ROOT, "%.1f", metrics.sourceFileCount() / (double) metrics.testFileCount())
                    + " source files.");
        }
        if (!declaredUnits.isEmpty()) {
            observations.add(declaredUnits.size() + " public/exported unit(s) were identified as candidates for "
                    + "unit-test generation.");
        }
        if (structure != null && !structure.dependencies().isEmpty()) {
            observations.add(structure.dependencies().size() + " declared dependencies were parsed from the "
                    + "project's manifests.");
        }
        Map<String, Long> byCategory = new LinkedHashMap<>();
        findings.forEach(f -> byCategory.merge(f.category(), 1L, Long::sum));
        byCategory.forEach((category, count) ->
                observations.add(category.replace('_', ' ') + ": " + count + " location(s) flagged."));
        return observations;
    }

    // ─── Unit discovery for test scaffolding ─────────────────────────────────────

    /**
     * Records real declared unit names as {@code relativePath#unitName} so generated unit tests refer
     * to code that actually exists instead of an invented class name.
     */
    private void collectDeclaredUnits(String extension, String content, String relativePath, Set<String> sink) {
        if (sink.size() > 400) {
            return;
        }
        Matcher matcher = switch (extension) {
            case "java", "kt", "scala" -> JVM_METHOD.matcher(content);
            case "js", "jsx", "ts", "tsx" -> JS_EXPORTED_FN.matcher(content);
            case "py" -> PY_DEF.matcher(content);
            default -> null;
        };
        if (matcher == null) {
            return;
        }
        while (matcher.find() && sink.size() <= 400) {
            String name = matcher.group(1) != null ? matcher.group(1)
                    : (matcher.groupCount() > 1 ? matcher.group(2) : null);
            if (name != null && !name.isBlank() && !isNoiseUnitName(name)) {
                sink.add(relativePath + "#" + name);
            }
        }
    }

    private boolean isNoiseUnitName(String name) {
        return switch (name) {
            case "main", "toString", "equals", "hashCode", "get", "set", "if", "for", "while",
                 "switch", "catch", "return", "new", "class", "record" -> true;
            default -> name.length() < 3;
        };
    }

    // ─── Small helpers ───────────────────────────────────────────────────────────

    private String relativize(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String truncate(String value) {
        return value.length() > MAX_EVIDENCE_CHARS ? value.substring(0, MAX_EVIDENCE_CHARS) + "…" : value;
    }
}
