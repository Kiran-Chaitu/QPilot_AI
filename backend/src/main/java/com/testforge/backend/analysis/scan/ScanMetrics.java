package com.testforge.backend.analysis.scan;

import java.util.List;
import java.util.Map;

/**
 * Counts measured by walking the project's real files. Nothing here is estimated — each field is the
 * result of counting something that exists on disk, which is what allows the dashboard and risk score
 * to be defensible rather than decorative.
 *
 * @param filesScanned               total files inspected (after build-artifact exclusions)
 * @param sourceFileCount            files with a recognized application-source extension
 * @param testFileCount              files identified as tests by path/name convention
 * @param totalLinesOfCode           summed physical lines across source files
 * @param nonBlankLinesOfCode        summed non-blank, non-comment-only lines across source files
 * @param endpointCount              HTTP routes discovered by static route scanning
 * @param endpointsReferencedByTests routes whose literal path string appears inside a test file
 * @param dependencyCount            declared dependencies parsed from manifests
 * @param languageBreakdown          file counts per detected language
 * @param testFrameworksDetected     test frameworks found in dependency manifests / imports
 * @param unavailableChecks          checks that could not run here, each with the reason
 */
public record ScanMetrics(
        int filesScanned,
        int sourceFileCount,
        int testFileCount,
        long totalLinesOfCode,
        long nonBlankLinesOfCode,
        int endpointCount,
        int endpointsReferencedByTests,
        int dependencyCount,
        Map<String, Long> languageBreakdown,
        List<String> testFrameworksDetected,
        List<String> unavailableChecks
) {
}
