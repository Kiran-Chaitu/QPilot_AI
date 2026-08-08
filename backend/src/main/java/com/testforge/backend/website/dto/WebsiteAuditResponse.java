package com.testforge.backend.website.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a live website audit.
 *
 * <p>Two conventions run through this whole payload, and they are the reason it can be trusted:
 *
 * <ul>
 *   <li><b>Unreachable is a result, not an error.</b> When the target cannot be fetched,
 *       {@code reachable} is false and {@code failureReason} explains exactly why (DNS, TLS, timeout,
 *       refused). The audit still completed — it found that the site is down — so the UI renders a
 *       truthful report rather than a blank screen or invented metrics.</li>
 *   <li><b>Nullable scores mean "not measured".</b> Every score is a boxed type. A null score is
 *       rendered as "Not available" with a reason in {@code unavailableChecks}, never silently
 *       replaced by a plausible-looking default.</li>
 * </ul>
 *
 * @param scores           category scores, each null when it could not be computed
 * @param unavailableChecks checks that could not run, each with its reason
 */
public record WebsiteAuditResponse(
        String targetUrl,
        String finalUrl,
        boolean reachable,
        String failureReason,
        Integer responseCode,
        String responseStatusText,
        Long responseTimeMs,
        Long dnsLookupMs,
        Long tlsHandshakeMs,
        Long contentLengthBytes,
        String contentType,
        String httpProtocolVersion,
        Boolean httpsEnabled,
        TlsInfo tls,
        List<RedirectHop> redirectChain,

        PageMetadata page,
        Scores scores,
        List<HeaderCheck> securityHeaders,
        List<HeaderCheck> allResponseHeaders,
        List<CookieCheck> cookies,
        List<LinkCheck> links,
        SeoChecks seo,
        AccessibilityChecks accessibility,
        List<String> detectedTechnologies,
        RobotsInfo robots,

        List<Recommendation> recommendations,
        List<String> unavailableChecks,
        long auditDurationMs,
        Instant auditedAt
) {

    /** TLS facts read from the negotiated session. Null fields mean the value was not exposed. */
    public record TlsInfo(String protocol, String cipherSuite, String certificateSubject,
                          String certificateIssuer, Instant certificateExpiresAt, Long daysUntilExpiry,
                          Boolean certificateValid, String certificateError) {
    }

    public record RedirectHop(int hop, String url, int statusCode, String location) {
    }

    /** Values parsed out of the returned HTML. Null means the element was absent. */
    public record PageMetadata(String title, Integer titleLength, String metaDescription,
                               Integer metaDescriptionLength, String langAttribute, String charset,
                               Boolean hasViewportMeta, Integer h1Count, String firstH1,
                               Integer imageCount, Integer imagesMissingAlt, Integer scriptCount,
                               Integer stylesheetCount, Integer inlineScriptCount, Integer totalLinkCount,
                               Integer internalLinkCount, Integer externalLinkCount, Integer htmlSizeBytes) {
    }

    /**
     * Category scores in the 0-100 range, each computed from named checks. Every score is nullable
     * because a score that could not be measured must read as "not available", not as zero (which would
     * look like a catastrophic failure) or as a mid-range default (which would look like a measurement).
     *
     * @param breakdown per-score explanation of how the number was reached
     */
    public record Scores(Integer security, Integer seo, Integer accessibility, Integer bestPractices,
                         Integer performance, Map<String, List<String>> breakdown) {
    }

    public record HeaderCheck(String name, String value, boolean present, String assessment, String severity) {
    }

    /** Security attributes of a Set-Cookie header, all observed rather than assumed. */
    public record CookieCheck(String name, boolean secure, boolean httpOnly, String sameSite,
                              boolean hasExpiry, String assessment) {
    }

    /**
     * A link that was actually requested. {@code statusCode} is null and {@code error} populated when the
     * request failed outright, so a broken link is reported as broken rather than as a fabricated 200.
     */
    public record LinkCheck(String url, boolean internal, Integer statusCode, String statusText,
                            Long responseTimeMs, boolean broken, String error) {
    }

    public record SeoChecks(Boolean hasTitle, Boolean titleLengthOptimal, Boolean hasMetaDescription,
                            Boolean metaDescriptionLengthOptimal, Boolean hasSingleH1, Boolean hasCanonical,
                            String canonicalUrl, Boolean hasOpenGraph, Boolean hasStructuredData,
                            Boolean robotsIndexable, List<String> issues) {
    }

    /**
     * Automatable accessibility checks only. {@code note} states plainly that these are a subset of WCAG:
     * contrast, focus order and screen-reader behaviour need a rendering engine and human judgement, so
     * claiming a full WCAG verdict from an HTML fetch would be false.
     */
    public record AccessibilityChecks(Boolean hasLangAttribute, Integer imagesMissingAlt, Integer totalImages,
                                      Integer inputsMissingLabel, Integer totalInputs, Boolean hasSkipLink,
                                      Boolean hasPageTitle, Integer emptyLinkCount, Integer emptyButtonCount,
                                      List<String> issues, String note) {
    }

    public record RobotsInfo(Boolean robotsTxtFound, Boolean pathAllowed, Boolean sitemapDeclared,
                             String sitemapUrl, String note) {
    }

    public record Recommendation(String category, String severity, String title, String detail) {
    }
}
