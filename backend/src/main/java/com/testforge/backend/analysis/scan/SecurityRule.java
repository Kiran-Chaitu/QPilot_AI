package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.Severity;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A single deterministic source-code check.
 *
 * @param id             stable rule identifier reported with every finding
 * @param category       finding category shown in the UI
 * @param severity       severity assigned when this rule matches
 * @param pattern        regex applied line-by-line to file contents
 * @param description    what the match means (the concrete file:line is appended by the engine)
 * @param recommendation how to fix it
 * @param extensions     file extensions this rule applies to; empty means "all scanned files"
 * @param falsePositiveFilter optional regex — when it matches the same line, the finding is suppressed
 *                            (used to skip placeholders, environment-variable lookups and the like)
 * @param contextQualifier optional regex that must ALSO match within {@link #contextLines} lines above
 *                         the hit. This exists because the dangerous thing and the reason it is
 *                         dangerous are often on different lines: {@code new Random()} is unremarkable
 *                         on its own but not inside a method called {@code generateToken}. Requiring the
 *                         qualifier keeps such rules precise instead of firing on every use of the API.
 * @param contextLines   how many preceding lines the qualifier may appear in
 */
public record SecurityRule(
        String id,
        String category,
        Severity severity,
        Pattern pattern,
        String description,
        String recommendation,
        Set<String> extensions,
        Pattern falsePositiveFilter,
        Pattern contextQualifier,
        int contextLines
) {

    public boolean appliesTo(String extension) {
        return extensions.isEmpty() || extensions.contains(extension);
    }

    public boolean isSuppressed(String line) {
        return falsePositiveFilter != null && falsePositiveFilter.matcher(line).find();
    }

    public boolean requiresContext() {
        return contextQualifier != null;
    }

    /**
     * Checks the qualifier against the matched line and the {@link #contextLines} lines above it.
     *
     * @param lines     the whole file, split into lines
     * @param lineIndex 0-based index of the line that matched {@link #pattern}
     */
    public boolean contextSatisfied(String[] lines, int lineIndex) {
        if (contextQualifier == null) {
            return true;
        }
        int from = Math.max(0, lineIndex - contextLines);
        for (int i = from; i <= lineIndex; i++) {
            if (contextQualifier.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }
}
