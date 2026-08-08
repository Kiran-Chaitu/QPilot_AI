package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.Severity;

/**
 * One security/quality issue located in a real file at a real line.
 *
 * @param ruleId          identifier of the rule that matched, for traceability
 * @param category        finding category (SQL_INJECTION, HARDCODED_SECRET, ...)
 * @param severity        assessed severity for this rule
 * @param description     what was found, including the concrete file and line
 * @param recommendation  concrete remediation advice
 * @param filePath        project-relative path of the file the match was found in
 * @param lineNumber      1-based line number of the match
 * @param evidence        the actual source line that matched, trimmed and truncated
 * @param occurrenceCount total matches for this rule across the whole project
 */
public record ScanFinding(
        String ruleId,
        String category,
        Severity severity,
        String description,
        String recommendation,
        String filePath,
        int lineNumber,
        String evidence,
        int occurrenceCount
) {
}
