package com.testforge.backend.website.dto;

import java.util.List;

public record WebsiteAuditResponse(
        String targetUrl,
        int responseCode,
        long responseTimeMs,
        String pageTitle,
        int performanceScore,
        int accessibilityScore,
        int bestPracticesScore,
        int seoScore,
        int securityScore,
        List<HeaderAuditItem> headers,
        List<LinkAuditItem> links,
        List<String> recommendations
) {
    public record HeaderAuditItem(String name, String value, boolean present, String status) {}
    public record LinkAuditItem(String url, int statusCode, String statusText, long responseTimeMs) {}
}
