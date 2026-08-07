package com.testforge.backend.project.dto;

import java.util.List;
import java.util.Map;

/**
 * Result of statically analyzing an extracted project: what languages it's
 * written in, what it depends on, which API endpoints it exposes, and which
 * source files look most significant (used later as AI prompt context in
 * place of a full vector-DB/RAG pipeline).
 */
public record ProjectStructureSummary(
        int totalFiles,
        Map<String, Long> languageBreakdown,
        String primaryLanguage,
        List<String> dependencies,
        List<ApiEndpointSummary> endpoints,
        List<String> topLevelEntries,
        List<KeyFile> keyFiles
) {
    public record KeyFile(String relativePath, String excerpt) {
    }
}
