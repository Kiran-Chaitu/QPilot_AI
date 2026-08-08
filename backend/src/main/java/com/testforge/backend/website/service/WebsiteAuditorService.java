package com.testforge.backend.website.service;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.website.dto.WebsiteAuditRequest;
import com.testforge.backend.website.dto.WebsiteAuditResponse;
import com.testforge.backend.website.dto.WebsiteAuditResponse.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audits a live website using only what the target actually returns.
 *
 * <p>The previous implementation had a failure path that quietly produced a plausible-looking report:
 * on any exception it invented a response time from the system clock, substituted the page title
 * "Web Application Target", and — when no links were found in the HTML — appended three fictional links
 * (<code>/about</code>, <code>/api/v1/health</code>, <code>/docs</code>) each reported as a 200 with a
 * made-up latency. Accessibility and SEO scores were literals chosen by whether the URL contained the
 * substring "https". A user could audit a domain that does not exist and receive a healthy-looking report.
 *
 * <p>This implementation holds to three rules:
 * <ol>
 *   <li><b>Measure or say nothing.</b> Every number is read from the response, the socket or the parsed
 *       HTML. Anything that could not be measured is null, and the reason lands in
 *       {@code unavailableChecks} so the UI can render "Not available".</li>
 *   <li><b>Failure is a finding.</b> An unreachable target produces a complete report with
 *       {@code reachable=false} and a specific {@code failureReason} — never a fabricated success and
 *       never a blank screen.</li>
 *   <li><b>Scores are explained.</b> Each category score carries the named checks that produced it, so a
 *       score of 50 can be traced to which checks failed instead of being taken on faith.</li>
 * </ol>
 */
@Service
public class WebsiteAuditorService {

    private static final Logger log = LoggerFactory.getLogger(WebsiteAuditorService.class);

    private static final String USER_AGENT = "QPilot-AI-WebsiteAuditor/3.0 (+quality-audit)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration LINK_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_REDIRECTS = 10;
    private static final int DEFAULT_MAX_LINKS = 15;
    private static final int MAX_HTML_CHARS_TO_PARSE = 3_000_000;

    // ─── HTML parsing patterns. Regex is adequate here: we extract a bounded set of well-known
    // ─── elements and attributes for reporting, not build a DOM.
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]+name\\s*=\\s*[\"']description[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESCRIPTION_REVERSED = Pattern.compile(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+name\\s*=\\s*[\"']description[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_LANG = Pattern.compile("<html[^>]+lang\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET = Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([\\w-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWPORT = Pattern.compile("<meta[^>]+name\\s*=\\s*[\"']viewport[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>(.*?)</h1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTR = Pattern.compile("\\balt\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_WITH_SRC = Pattern.compile("<script\\b[^>]*\\bsrc\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLESHEET = Pattern.compile(
            "<link[^>]+rel\\s*=\\s*[\"']stylesheet[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR = Pattern.compile("<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CANONICAL = Pattern.compile(
            "<link[^>]+rel\\s*=\\s*[\"']canonical[\"'][^>]+href\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_GRAPH = Pattern.compile("<meta[^>]+property\\s*=\\s*[\"']og:",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURED_DATA = Pattern.compile(
            "<script[^>]+type\\s*=\\s*[\"']application/ld\\+json[\"']|\\bitemscope\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_ROBOTS_NOINDEX = Pattern.compile(
            "<meta[^>]+name\\s*=\\s*[\"']robots[\"'][^>]+content\\s*=\\s*[\"'][^\"']*noindex", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_TAG = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_HIDDEN = Pattern.compile("type\\s*=\\s*[\"'](hidden|submit|button)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_OR_ARIA = Pattern.compile(
            "\\b(aria-label|aria-labelledby|title|placeholder)\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIP_LINK = Pattern.compile("href\\s*=\\s*[\"']#(main|content|skip)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUTTON_TAG = Pattern.compile("<button\\b[^>]*>(.*?)</button>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_STRIP = Pattern.compile("<[^>]+>");

    /** Security headers scored by the audit, with the severity of their absence. */
    private static final List<SecurityHeaderSpec> SECURITY_HEADER_SPECS = List.of(
            new SecurityHeaderSpec("Strict-Transport-Security", "HIGH",
                    "Forces browsers to use HTTPS for future visits, preventing a downgrade to plaintext."),
            new SecurityHeaderSpec("Content-Security-Policy", "HIGH",
                    "Restricts which scripts and resources may load — the strongest single mitigation for XSS."),
            new SecurityHeaderSpec("X-Content-Type-Options", "MEDIUM",
                    "Stops browsers from MIME-sniffing a response into a different content type."),
            new SecurityHeaderSpec("X-Frame-Options", "MEDIUM",
                    "Prevents the page being framed by another site (clickjacking)."),
            new SecurityHeaderSpec("Referrer-Policy", "LOW",
                    "Controls how much URL information leaks to third parties via the Referer header."),
            new SecurityHeaderSpec("Permissions-Policy", "LOW",
                    "Restricts access to powerful browser features such as camera and geolocation."));

    private record SecurityHeaderSpec(String name, String severityIfMissing, String why) {
    }

    private final HttpClient httpClient;

    public WebsiteAuditorService() {
        this.httpClient = HttpClient.newBuilder()
                // Redirects are followed manually so the chain can be reported. The client's automatic
                // following would hide it, and the chain is itself an audit finding (loops, downgrades
                // from HTTPS to HTTP, unnecessary hops that cost latency).
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public WebsiteAuditResponse audit(WebsiteAuditRequest request) {
        long auditStartNanos = System.nanoTime();
        URI target = validateUrl(request.targetUrl());
        List<String> unavailable = new ArrayList<>();

        // Measured before the HTTP request so DNS cost is attributed to DNS, not to the response time.
        DnsResult dns = resolveDns(target.getHost());
        if (!dns.resolved()) {
            return unreachableResponse(target, "DNS resolution failed for host \"" + target.getHost()
                    + "\": " + dns.error() + ". The domain may be misspelled, expired, or not publicly resolvable.",
                    dns.lookupMs(), auditStartNanos);
        }

        FetchResult fetch = fetchWithRedirects(target);
        if (!fetch.success()) {
            return unreachableResponse(target, fetch.failureReason(), dns.lookupMs(), auditStartNanos);
        }

        HttpResponse<String> response = fetch.finalResponse();
        HttpHeaders headers = response.headers();
        String html = response.body() != null ? response.body() : "";
        if (html.length() > MAX_HTML_CHARS_TO_PARSE) {
            html = html.substring(0, MAX_HTML_CHARS_TO_PARSE);
            unavailable.add("HTML analysis was truncated at " + MAX_HTML_CHARS_TO_PARSE
                    + " characters because the document is unusually large; element counts below cover "
                    + "the analyzed portion only.");
        }

        URI finalUri = response.uri();
        boolean https = "https".equalsIgnoreCase(finalUri.getScheme());

        TlsInfo tls = null;
        if (https) {
            tls = extractTlsInfo(response, unavailable);
        } else {
            unavailable.add("TLS inspection - not applicable: the final URL uses plain HTTP, so there is no "
                    + "TLS session to inspect.");
        }

        PageMetadata page = parsePageMetadata(html, finalUri);
        List<HeaderCheck> securityHeaders = checkSecurityHeaders(headers, https);
        List<HeaderCheck> allHeaders = listAllHeaders(headers);
        List<CookieCheck> cookies = checkCookies(headers);
        RobotsInfo robots = checkRobots(finalUri, unavailable);
        SeoChecks seo = checkSeo(html, page, robots);
        AccessibilityChecks accessibility = checkAccessibility(html, page);
        List<String> technologies = detectTechnologies(headers, html);

        List<LinkCheck> links;
        if (request.checkLinks()) {
            int maxLinks = request.maxLinksToCheck() != null ? request.maxLinksToCheck() : DEFAULT_MAX_LINKS;
            links = checkLinks(html, finalUri, maxLinks);
            if (links.isEmpty()) {
                unavailable.add("Link checking - no links found: the page contains no anchor href values to "
                        + "verify. This is normal for a client-rendered single-page application, whose links "
                        + "only exist after JavaScript executes.");
            }
        } else {
            links = List.of();
            unavailable.add("Link checking - not requested: enable it to have QPilot request each link on the "
                    + "page and report its real status code.");
        }

        Scores scores = computeScores(securityHeaders, seo, accessibility, page, fetch.responseTimeMs(),
                https, response.statusCode(), fetch.redirectChain().size(), cookies, unavailable);

        List<Recommendation> recommendations = buildRecommendations(securityHeaders, seo, accessibility, page,
                fetch.responseTimeMs(), https, tls, cookies, links, fetch.redirectChain());

        unavailable.add("Rendered-page performance (Core Web Vitals: LCP, CLS, INP) - not available: these "
                + "require a real browser rendering the page. The response time reported here is the "
                + "server's time-to-last-byte for the initial HTML document only.");
        unavailable.add("Full WCAG conformance - not available: contrast ratios, focus order and "
                + "screen-reader behaviour cannot be determined from HTML alone. Only the automatable "
                + "structural checks shown were performed.");

        long auditDurationMs = (System.nanoTime() - auditStartNanos) / 1_000_000;

        return new WebsiteAuditResponse(
                target.toString(), finalUri.toString(), true, null,
                response.statusCode(), describeStatus(response.statusCode()),
                fetch.responseTimeMs(), dns.lookupMs(), fetch.tlsHandshakeMs(),
                headers.firstValue("content-length").map(this::parseLongOrNull).orElse((long) html.length()),
                headers.firstValue("content-type").orElse(null),
                response.version().name(),
                https, tls, fetch.redirectChain(),
                page, scores, securityHeaders, allHeaders, cookies, links, seo, accessibility, technologies, robots,
                recommendations, unavailable, auditDurationMs, Instant.now());
    }

    // ─── Fetch with explicit redirect chain ──────────────────────────────────────

    private record FetchResult(boolean success, HttpResponse<String> finalResponse, List<RedirectHop> redirectChain,
                               Long responseTimeMs, Long tlsHandshakeMs, String failureReason) {
    }

    private FetchResult fetchWithRedirects(URI startUri) {
        List<RedirectHop> chain = new ArrayList<>();
        URI current = startUri;
        long totalNanos = 0;
        Long tlsHandshakeMs = null;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            long hopStart = System.nanoTime();
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(current)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long hopNanos = System.nanoTime() - hopStart;
                totalNanos += hopNanos;

                if (hop == 0 && response.sslSession().isPresent()) {
                    // The first hop's elapsed time includes the TLS handshake; it cannot be isolated from
                    // the JDK client, so it is reported as an upper bound rather than a precise figure.
                    tlsHandshakeMs = hopNanos / 1_000_000;
                }

                int status = response.statusCode();
                boolean isRedirect = status >= 300 && status < 400;
                Optional<String> location = response.headers().firstValue("location");

                if (isRedirect && location.isPresent()) {
                    URI next = current.resolve(location.get());
                    chain.add(new RedirectHop(hop + 1, current.toString(), status, next.toString()));
                    if (chain.stream().anyMatch(h -> h.url().equals(next.toString()))) {
                        return new FetchResult(false, null, chain, totalNanos / 1_000_000, tlsHandshakeMs,
                                "Redirect loop detected: " + next + " appears more than once in the redirect "
                                        + "chain, so the request can never resolve to a final page.");
                    }
                    current = next;
                    continue;
                }
                return new FetchResult(true, response, chain, totalNanos / 1_000_000, tlsHandshakeMs, null);

            } catch (HttpTimeoutException e) {
                return new FetchResult(false, null, chain, null, tlsHandshakeMs,
                        "The target did not respond within " + REQUEST_TIMEOUT.toSeconds() + " seconds. "
                                + "The server may be overloaded, or it may be silently dropping requests from "
                                + "unknown clients.");
            } catch (SSLHandshakeException e) {
                return new FetchResult(false, null, chain, null, tlsHandshakeMs,
                        "The TLS handshake failed: " + e.getMessage() + ". The certificate may be expired, "
                                + "self-signed, issued for a different hostname, or signed by an authority this "
                                + "JVM does not trust.");
            } catch (ConnectException e) {
                return new FetchResult(false, null, chain, null, tlsHandshakeMs,
                        "The connection to " + current.getHost() + " was refused or could not be established: "
                                + e.getMessage() + ". Nothing appears to be listening on the requested port.");
            } catch (IOException e) {
                return new FetchResult(false, null, chain, null, tlsHandshakeMs,
                        "A network error occurred while fetching " + current + ": "
                                + e.getClass().getSimpleName() + " - " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new FetchResult(false, null, chain, null, tlsHandshakeMs,
                        "The audit was interrupted before the target responded.");
            }
        }
        return new FetchResult(false, null, chain, totalNanos / 1_000_000, tlsHandshakeMs,
                "The target exceeded " + MAX_REDIRECTS + " redirects without reaching a final page.");
    }

    private WebsiteAuditResponse unreachableResponse(URI target, String reason, Long dnsMs, long auditStartNanos) {
        // A complete, honest report of a negative result. Scores are null (not zero, not defaults) so the
        // UI shows "Not available" instead of implying a measured failing grade.
        log.info("Website audit of {} could not reach the target: {}", target, reason);
        List<String> unavailable = List.of(
                "All content, header, SEO and accessibility checks - not available: the target could not be "
                        + "fetched, so there is no response to analyze.",
                "Scores are reported as unavailable rather than zero: no measurement was possible, which is "
                        + "different from measuring a failure.");
        return new WebsiteAuditResponse(
                target.toString(), null, false, reason,
                null, null, null, dnsMs, null, null, null, null,
                "https".equalsIgnoreCase(target.getScheme()), null, List.of(),
                null,
                new Scores(null, null, null, null, null, Map.of()),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), null,
                List.of(new Recommendation("AVAILABILITY", "CRITICAL", "The target is not reachable", reason)),
                unavailable, (System.nanoTime() - auditStartNanos) / 1_000_000, Instant.now());
    }

    // ─── DNS ─────────────────────────────────────────────────────────────────────

    private record DnsResult(boolean resolved, Long lookupMs, String error) {
    }

    private DnsResult resolveDns(String host) {
        long start = System.nanoTime();
        try {
            InetAddress.getAllByName(host);
            return new DnsResult(true, (System.nanoTime() - start) / 1_000_000, null);
        } catch (UnknownHostException e) {
            return new DnsResult(false, (System.nanoTime() - start) / 1_000_000, "host could not be resolved");
        }
    }

    // ─── TLS ─────────────────────────────────────────────────────────────────────

    private TlsInfo extractTlsInfo(HttpResponse<String> response, List<String> unavailable) {
        Optional<SSLSession> maybeSession = response.sslSession();
        if (maybeSession.isEmpty()) {
            unavailable.add("TLS session details - not available: the HTTP client did not expose a TLS session "
                    + "for this response.");
            return null;
        }
        SSLSession session = maybeSession.get();
        String protocol = session.getProtocol();
        String cipher = session.getCipherSuite();

        try {
            java.security.cert.Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate cert)) {
                return new TlsInfo(protocol, cipher, null, null, null, null, null,
                        "The peer presented no X.509 certificate that could be inspected.");
            }
            Instant expiresAt = cert.getNotAfter().toInstant();
            long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), expiresAt);

            String validityError = null;
            boolean valid = true;
            try {
                cert.checkValidity();
            } catch (CertificateExpiredException e) {
                valid = false;
                validityError = "The certificate expired on " + expiresAt + ".";
            } catch (CertificateNotYetValidException e) {
                valid = false;
                validityError = "The certificate is not valid until " + cert.getNotBefore().toInstant() + ".";
            }

            return new TlsInfo(protocol, cipher,
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    expiresAt, daysUntilExpiry, valid, validityError);

        } catch (Exception e) {
            unavailable.add("Certificate details - not available: " + e.getMessage());
            return new TlsInfo(protocol, cipher, null, null, null, null, null, e.getMessage());
        }
    }

    // ─── HTML parsing ────────────────────────────────────────────────────────────

    private PageMetadata parsePageMetadata(String html, URI baseUri) {
        String title = firstGroup(TITLE, html);
        if (title != null) {
            title = TAG_STRIP.matcher(title).replaceAll("").trim();
        }
        String metaDescription = firstGroup(META_DESCRIPTION, html);
        if (metaDescription == null) {
            // Attribute order in a meta tag is arbitrary, so the reversed form is checked too rather
            // than reporting a description as absent when it is merely written the other way round.
            metaDescription = firstGroup(META_DESCRIPTION_REVERSED, html);
        }

        List<String> h1s = allGroups(H1, html);
        int imageCount = countMatches(IMG_TAG, html);
        int imagesMissingAlt = 0;
        Matcher imgMatcher = IMG_TAG.matcher(html);
        while (imgMatcher.find()) {
            if (!ALT_ATTR.matcher(imgMatcher.group()).find()) {
                imagesMissingAlt++;
            }
        }

        int scriptTotal = countMatches(SCRIPT_TAG, html);
        int scriptWithSrc = countMatches(SCRIPT_WITH_SRC, html);

        int internal = 0;
        int external = 0;
        String host = baseUri.getHost();
        Matcher anchorMatcher = ANCHOR.matcher(html);
        while (anchorMatcher.find()) {
            String href = anchorMatcher.group(1).trim();
            if (href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")
                    || href.startsWith("javascript:")) {
                continue;
            }
            try {
                URI resolved = baseUri.resolve(href);
                if (resolved.getHost() != null && resolved.getHost().equalsIgnoreCase(host)) {
                    internal++;
                } else if (resolved.getHost() != null) {
                    external++;
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed href — not counted either way rather than guessed at.
            }
        }

        return new PageMetadata(
                emptyToNull(title), title != null ? title.length() : null,
                emptyToNull(metaDescription), metaDescription != null ? metaDescription.length() : null,
                firstGroup(HTML_LANG, html), firstGroup(CHARSET, html),
                VIEWPORT.matcher(html).find(),
                h1s.size(),
                h1s.isEmpty() ? null : TAG_STRIP.matcher(h1s.get(0)).replaceAll("").trim(),
                imageCount, imagesMissingAlt,
                scriptWithSrc, countMatches(STYLESHEET, html),
                Math.max(0, scriptTotal - scriptWithSrc),
                internal + external, internal, external, html.length());
    }

    // ─── Header checks ───────────────────────────────────────────────────────────

    private List<HeaderCheck> checkSecurityHeaders(HttpHeaders headers, boolean https) {
        List<HeaderCheck> checks = new ArrayList<>();
        for (SecurityHeaderSpec spec : SECURITY_HEADER_SPECS) {
            Optional<String> value = headers.firstValue(spec.name().toLowerCase(Locale.ROOT));
            boolean present = value.isPresent() && !value.get().isBlank();

            String assessment;
            String severity;
            if (present) {
                assessment = assessHeaderValue(spec.name(), value.get());
                severity = "OK";
            } else if (spec.name().equals("Strict-Transport-Security") && !https) {
                // HSTS is ignored by browsers over plain HTTP, so flagging its absence here would be
                // technically true and practically misleading — the real problem is the missing HTTPS.
                assessment = "Not applicable over plain HTTP. Browsers ignore HSTS on non-HTTPS responses; "
                        + "enable HTTPS first.";
                severity = "INFO";
            } else {
                assessment = "Missing. " + spec.why();
                severity = spec.severityIfMissing();
            }
            checks.add(new HeaderCheck(spec.name(), value.orElse(null), present, assessment, severity));
        }
        return checks;
    }

    /** Assesses a present header's actual value — presence alone is not the same as being configured well. */
    private String assessHeaderValue(String name, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "Strict-Transport-Security" -> {
                long maxAge = extractMaxAge(lower);
                if (maxAge < 0) {
                    yield "Present but no max-age directive was found, so browsers have no duration to enforce.";
                } else if (maxAge < 15_552_000) {
                    yield "Present, but max-age=" + maxAge + " is under the 180-day minimum recommended for "
                            + "preload eligibility.";
                }
                yield "Enforced with max-age=" + maxAge
                        + (lower.contains("includesubdomains") ? " and includeSubDomains." : ". Consider "
                        + "adding includeSubDomains so subdomains are covered too.");
            }
            case "Content-Security-Policy" -> {
                if (lower.contains("unsafe-inline") && lower.contains("script-src")) {
                    yield "Present, but script-src allows 'unsafe-inline', which permits inline script "
                            + "execution and removes most of the policy's XSS protection.";
                }
                if (lower.contains("unsafe-eval")) {
                    yield "Present, but allows 'unsafe-eval', permitting dynamic code evaluation.";
                }
                yield "Active and does not contain the common unsafe-inline / unsafe-eval weakeners.";
            }
            case "X-Content-Type-Options" -> lower.contains("nosniff")
                    ? "Set to nosniff." : "Present but not set to 'nosniff', so it has no effect.";
            case "X-Frame-Options" -> (lower.contains("deny") || lower.contains("sameorigin"))
                    ? "Set to " + value + "." : "Present but the value is not DENY or SAMEORIGIN.";
            default -> "Present: " + value;
        };
    }

    private long extractMaxAge(String headerValue) {
        Matcher matcher = Pattern.compile("max-age\\s*=\\s*(\\d+)").matcher(headerValue);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    private List<HeaderCheck> listAllHeaders(HttpHeaders headers) {
        List<HeaderCheck> all = new ArrayList<>();
        headers.map().forEach((name, values) -> all.add(new HeaderCheck(
                name, String.join("; ", values), true, null, "INFO")));
        all.sort(Comparator.comparing(HeaderCheck::name));
        return all;
    }

    private List<CookieCheck> checkCookies(HttpHeaders headers) {
        List<CookieCheck> checks = new ArrayList<>();
        for (String setCookie : headers.allValues("set-cookie")) {
            String lower = setCookie.toLowerCase(Locale.ROOT);
            String name = setCookie.contains("=") ? setCookie.substring(0, setCookie.indexOf('=')).trim() : setCookie;
            boolean secure = lower.contains("; secure") || lower.endsWith(";secure");
            boolean httpOnly = lower.contains("httponly");
            String sameSite = null;
            Matcher sameSiteMatcher = Pattern.compile("samesite\\s*=\\s*(\\w+)", Pattern.CASE_INSENSITIVE)
                    .matcher(setCookie);
            if (sameSiteMatcher.find()) {
                sameSite = sameSiteMatcher.group(1);
            }
            boolean hasExpiry = lower.contains("expires=") || lower.contains("max-age=");

            List<String> problems = new ArrayList<>();
            if (!secure) {
                problems.add("missing Secure (may be sent over plaintext HTTP)");
            }
            if (!httpOnly) {
                problems.add("missing HttpOnly (readable by JavaScript, so exposed to XSS)");
            }
            if (sameSite == null) {
                problems.add("no SameSite attribute (browser defaults vary; set it explicitly)");
            }
            checks.add(new CookieCheck(name, secure, httpOnly, sameSite, hasExpiry,
                    problems.isEmpty() ? "All recommended protection attributes are set."
                            : "Issues: " + String.join("; ", problems) + "."));
        }
        return checks;
    }

    // ─── robots.txt ──────────────────────────────────────────────────────────────

    /**
     * Fetches robots.txt to report crawlability. Deliberately informational: QPilot audits a single URL
     * the user explicitly asked about, which is not crawling, but whether that URL is indexable is a real
     * SEO finding.
     */
    private RobotsInfo checkRobots(URI finalUri, List<String> unavailable) {
        try {
            URI robotsUri = new URI(finalUri.getScheme(), null, finalUri.getHost(), finalUri.getPort(),
                    "/robots.txt", null, null);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(robotsUri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(LINK_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new RobotsInfo(false, true, false, null,
                        "No robots.txt was served (HTTP " + response.statusCode()
                                + "), so no crawl restrictions are declared.");
            }
            String body = response.body() != null ? response.body() : "";
            String sitemap = null;
            Matcher sitemapMatcher = Pattern.compile("(?im)^\\s*sitemap\\s*:\\s*(\\S+)").matcher(body);
            if (sitemapMatcher.find()) {
                sitemap = sitemapMatcher.group(1);
            }
            boolean disallowsAll = Pattern.compile("(?im)^\\s*disallow\\s*:\\s*/\\s*$").matcher(body).find();
            return new RobotsInfo(true, !disallowsAll, sitemap != null, sitemap,
                    disallowsAll ? "robots.txt disallows all crawling (Disallow: /)."
                            : "robots.txt was found and does not block the site root.");
        } catch (Exception e) {
            unavailable.add("robots.txt check - not available: " + e.getClass().getSimpleName()
                    + " while fetching /robots.txt.");
            return null;
        }
    }

    // ─── SEO & accessibility ─────────────────────────────────────────────────────

    private SeoChecks checkSeo(String html, PageMetadata page, RobotsInfo robots) {
        List<String> issues = new ArrayList<>();

        boolean hasTitle = page.title() != null;
        Boolean titleOptimal = null;
        if (hasTitle) {
            int length = page.titleLength();
            titleOptimal = length >= 30 && length <= 60;
            if (!titleOptimal) {
                issues.add("The <title> is " + length + " characters; 30-60 is the range that displays fully "
                        + "in search results without truncation.");
            }
        } else {
            issues.add("The page has no <title> element, so search engines and browser tabs have no name for it.");
        }

        boolean hasDescription = page.metaDescription() != null;
        Boolean descriptionOptimal = null;
        if (hasDescription) {
            int length = page.metaDescriptionLength();
            descriptionOptimal = length >= 70 && length <= 160;
            if (!descriptionOptimal) {
                issues.add("The meta description is " + length + " characters; 70-160 is the range typically "
                        + "shown in full by search engines.");
            }
        } else {
            issues.add("No meta description is present, so search engines will synthesize the snippet from "
                    + "page content.");
        }

        boolean singleH1 = page.h1Count() != null && page.h1Count() == 1;
        if (page.h1Count() != null && page.h1Count() == 0) {
            issues.add("The page has no <h1>, leaving its primary heading undefined.");
        } else if (page.h1Count() != null && page.h1Count() > 1) {
            issues.add("The page has " + page.h1Count() + " <h1> elements; exactly one primary heading is "
                    + "the convention.");
        }

        String canonical = firstGroup(CANONICAL, html);
        if (canonical == null) {
            issues.add("No canonical link is declared, so duplicate URLs for this page may compete in search.");
        }
        boolean openGraph = OPEN_GRAPH.matcher(html).find();
        if (!openGraph) {
            issues.add("No Open Graph tags found, so link previews on social platforms will be generic.");
        }
        boolean structuredData = STRUCTURED_DATA.matcher(html).find();

        boolean metaNoindex = META_ROBOTS_NOINDEX.matcher(html).find();
        Boolean indexable = null;
        if (metaNoindex) {
            indexable = false;
            issues.add("A robots meta tag declares noindex, so this page will be excluded from search results.");
        } else if (robots != null && robots.pathAllowed() != null) {
            indexable = robots.pathAllowed();
        }

        return new SeoChecks(hasTitle, titleOptimal, hasDescription, descriptionOptimal, singleH1,
                canonical != null, canonical, openGraph, structuredData, indexable, issues);
    }

    private AccessibilityChecks checkAccessibility(String html, PageMetadata page) {
        List<String> issues = new ArrayList<>();

        boolean hasLang = page.langAttribute() != null && !page.langAttribute().isBlank();
        if (!hasLang) {
            issues.add("The <html> element has no lang attribute, so screen readers cannot select the correct "
                    + "pronunciation rules.");
        }
        if (page.imagesMissingAlt() != null && page.imagesMissingAlt() > 0) {
            issues.add(page.imagesMissingAlt() + " of " + page.imageCount() + " <img> elements have no alt "
                    + "attribute, so their content is unavailable to screen-reader users.");
        }

        int totalInputs = 0;
        int inputsMissingLabel = 0;
        Matcher inputMatcher = INPUT_TAG.matcher(html);
        while (inputMatcher.find()) {
            String tag = inputMatcher.group();
            if (INPUT_HIDDEN.matcher(tag).find()) {
                continue; // hidden/submit/button inputs need no visible label
            }
            totalInputs++;
            if (!LABEL_OR_ARIA.matcher(tag).find() && !tag.toLowerCase(Locale.ROOT).contains("id=")) {
                inputsMissingLabel++;
            }
        }
        if (inputsMissingLabel > 0) {
            issues.add(inputsMissingLabel + " of " + totalInputs + " form inputs have no discoverable label "
                    + "(no aria-label, title, placeholder or id for a <label for> to reference).");
        }

        int emptyLinks = 0;
        Matcher anchorMatcher = ANCHOR.matcher(html);
        while (anchorMatcher.find()) {
            String text = TAG_STRIP.matcher(anchorMatcher.group(2)).replaceAll("").trim();
            if (text.isEmpty() && !anchorMatcher.group().toLowerCase(Locale.ROOT).contains("aria-label")) {
                emptyLinks++;
            }
        }
        if (emptyLinks > 0) {
            issues.add(emptyLinks + " link(s) have no text content and no aria-label, so they are announced "
                    + "only as \"link\".");
        }

        int emptyButtons = 0;
        Matcher buttonMatcher = BUTTON_TAG.matcher(html);
        while (buttonMatcher.find()) {
            String text = TAG_STRIP.matcher(buttonMatcher.group(1)).replaceAll("").trim();
            if (text.isEmpty() && !buttonMatcher.group().toLowerCase(Locale.ROOT).contains("aria-label")) {
                emptyButtons++;
            }
        }
        if (emptyButtons > 0) {
            issues.add(emptyButtons + " button(s) have no accessible name.");
        }

        boolean skipLink = SKIP_LINK.matcher(html).find();
        boolean hasTitle = page.title() != null;
        if (!hasTitle) {
            issues.add("The page has no title, which screen readers announce first on page load.");
        }

        return new AccessibilityChecks(hasLang, page.imagesMissingAlt(), page.imageCount(),
                inputsMissingLabel, totalInputs, skipLink, hasTitle, emptyLinks, emptyButtons, issues,
                "These are the structural WCAG checks that can be determined from HTML alone. Colour "
                        + "contrast, keyboard focus order, ARIA correctness and screen-reader behaviour require "
                        + "a rendering engine and human review, and were not assessed.");
    }

    private List<String> detectTechnologies(HttpHeaders headers, String html) {
        Set<String> detected = new LinkedHashSet<>();

        headers.firstValue("server").ifPresent(v -> detected.add("Server: " + v));
        headers.firstValue("x-powered-by").ifPresent(v -> detected.add("X-Powered-By: " + v));
        headers.firstValue("x-aspnet-version").ifPresent(v -> detected.add("ASP.NET " + v));
        if (headers.firstValue("cf-ray").isPresent()) {
            detected.add("Cloudflare");
        }
        if (headers.firstValue("x-vercel-id").isPresent()) {
            detected.add("Vercel");
        }
        if (headers.firstValue("x-amz-cf-id").isPresent()) {
            detected.add("Amazon CloudFront");
        }

        // Fingerprints from markup the framework itself emits, so these are observations rather than guesses.
        Map<String, String> htmlMarkers = new LinkedHashMap<>();
        htmlMarkers.put("__NEXT_DATA__", "Next.js");
        htmlMarkers.put("__NUXT__", "Nuxt");
        htmlMarkers.put("ng-version", "Angular");
        htmlMarkers.put("data-reactroot", "React");
        htmlMarkers.put("wp-content", "WordPress");
        htmlMarkers.put("Shopify.theme", "Shopify");
        htmlMarkers.put("data-svelte", "Svelte");
        htmlMarkers.put("/_vercel/insights", "Vercel Analytics");
        htmlMarkers.put("gtag(", "Google Analytics");
        htmlMarkers.forEach((marker, technology) -> {
            if (html.contains(marker)) {
                detected.add(technology);
            }
        });

        if (detected.isEmpty()) {
            detected.add("No identifying technology fingerprints were found in the response headers or HTML.");
        }
        return new ArrayList<>(detected);
    }

    // ─── Link checking ───────────────────────────────────────────────────────────

    /**
     * Requests each discovered link and reports its real status. Runs concurrently on virtual threads and
     * is bounded by {@code maxLinks}, so an audit cannot escalate into a crawl of the target.
     */
    private List<LinkCheck> checkLinks(String html, URI baseUri, int maxLinks) {
        LinkedHashMap<String, Boolean> candidates = new LinkedHashMap<>();
        String host = baseUri.getHost();

        Matcher matcher = ANCHOR.matcher(html);
        while (matcher.find() && candidates.size() < maxLinks) {
            String href = matcher.group(1).trim();
            if (href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")
                    || href.startsWith("javascript:") || href.startsWith("data:")) {
                continue;
            }
            try {
                URI resolved = baseUri.resolve(href);
                if (resolved.getScheme() == null || !resolved.getScheme().startsWith("http")) {
                    continue;
                }
                boolean internal = resolved.getHost() != null && resolved.getHost().equalsIgnoreCase(host);
                candidates.putIfAbsent(resolved.toString(), internal);
            } catch (IllegalArgumentException ignored) {
                // Unparseable href: skipped rather than reported with an invented status.
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<LinkCheck> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<LinkCheck>> futures = candidates.entrySet().stream()
                    .map(entry -> executor.submit(() -> checkOneLink(entry.getKey(), entry.getValue())))
                    .toList();
            for (Future<LinkCheck> future : futures) {
                try {
                    results.add(future.get(LINK_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS));
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        return results;
    }

    private LinkCheck checkOneLink(String url, boolean internal) {
        long start = System.nanoTime();
        try {
            // HEAD first: cheaper for both sides. Many servers answer 405 to HEAD, so a GET fallback
            // follows — without it those links would be misreported as broken.
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(LINK_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            if (status == 405 || status == 501) {
                start = System.nanoTime();
                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(LINK_TIMEOUT)
                        .GET()
                        .build();
                response = httpClient.send(getRequest, HttpResponse.BodyHandlers.discarding());
                status = response.statusCode();
            }

            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            boolean broken = status >= 400;
            return new LinkCheck(url, internal, status, describeStatus(status), latencyMs, broken, null);

        } catch (Exception e) {
            return new LinkCheck(url, internal, null, null, (System.nanoTime() - start) / 1_000_000, true,
                    e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    // ─── Scoring ─────────────────────────────────────────────────────────────────

    /**
     * Computes category scores from the checks that actually ran, recording each contribution.
     *
     * <p>Scores start at 100 and lose points for specific observed defects. A category with nothing
     * measurable yields null rather than a number, because "we could not tell" and "it scored badly" are
     * different statements and the UI must be able to distinguish them.
     */
    private Scores computeScores(List<HeaderCheck> securityHeaders, SeoChecks seo,
                                 AccessibilityChecks accessibility, PageMetadata page,
                                 Long responseTimeMs, boolean https, int statusCode,
                                 int redirectCount, List<CookieCheck> cookies, List<String> unavailable) {
        Map<String, List<String>> breakdown = new LinkedHashMap<>();

        // Security: HTTPS plus the weighted security headers plus cookie attributes.
        List<String> securityNotes = new ArrayList<>();
        int security = 100;
        if (!https) {
            security -= 30;
            securityNotes.add("-30: the final URL is served over plain HTTP, not HTTPS");
        }
        for (HeaderCheck header : securityHeaders) {
            if (!header.present() && !"INFO".equals(header.severity())) {
                int penalty = switch (header.severity()) {
                    case "HIGH" -> 15;
                    case "MEDIUM" -> 8;
                    default -> 4;
                };
                security -= penalty;
                securityNotes.add("-" + penalty + ": " + header.name() + " is missing");
            } else if (header.present() && header.assessment() != null
                    && header.assessment().startsWith("Present, but")) {
                security -= 5;
                securityNotes.add("-5: " + header.name() + " is present but weakly configured");
            }
        }
        long insecureCookies = cookies.stream().filter(c -> !c.secure() || !c.httpOnly()).count();
        if (insecureCookies > 0) {
            int penalty = (int) Math.min(15, insecureCookies * 5);
            security -= penalty;
            securityNotes.add("-" + penalty + ": " + insecureCookies
                    + " cookie(s) missing Secure and/or HttpOnly");
        }
        security = clampScore(security);
        securityNotes.add(0, "Starts at 100; points are deducted per observed defect. Final: " + security);
        breakdown.put("security", securityNotes);

        // SEO
        Integer seoScore = null;
        if (seo != null) {
            List<String> seoNotes = new ArrayList<>();
            int score = 100;
            if (!Boolean.TRUE.equals(seo.hasTitle())) {
                score -= 25;
                seoNotes.add("-25: no <title> element");
            } else if (Boolean.FALSE.equals(seo.titleLengthOptimal())) {
                score -= 8;
                seoNotes.add("-8: <title> length outside the 30-60 character range");
            }
            if (!Boolean.TRUE.equals(seo.hasMetaDescription())) {
                score -= 15;
                seoNotes.add("-15: no meta description");
            } else if (Boolean.FALSE.equals(seo.metaDescriptionLengthOptimal())) {
                score -= 6;
                seoNotes.add("-6: meta description length outside the 70-160 character range");
            }
            if (!Boolean.TRUE.equals(seo.hasSingleH1())) {
                score -= 12;
                seoNotes.add("-12: not exactly one <h1>");
            }
            if (!Boolean.TRUE.equals(seo.hasCanonical())) {
                score -= 8;
                seoNotes.add("-8: no canonical link");
            }
            if (!Boolean.TRUE.equals(seo.hasOpenGraph())) {
                score -= 8;
                seoNotes.add("-8: no Open Graph tags");
            }
            if (!Boolean.TRUE.equals(seo.hasStructuredData())) {
                score -= 5;
                seoNotes.add("-5: no structured data (JSON-LD or microdata)");
            }
            if (Boolean.FALSE.equals(seo.robotsIndexable())) {
                score -= 30;
                seoNotes.add("-30: the page is declared non-indexable");
            }
            seoScore = clampScore(score);
            seoNotes.add(0, "Starts at 100; points are deducted per observed defect. Final: " + seoScore);
            breakdown.put("seo", seoNotes);
        }

        // Accessibility — automatable structural checks only
        Integer accessibilityScore = null;
        if (accessibility != null) {
            List<String> notes = new ArrayList<>();
            int score = 100;
            if (!Boolean.TRUE.equals(accessibility.hasLangAttribute())) {
                score -= 15;
                notes.add("-15: <html> has no lang attribute");
            }
            if (accessibility.totalImages() != null && accessibility.totalImages() > 0
                    && accessibility.imagesMissingAlt() != null && accessibility.imagesMissingAlt() > 0) {
                int penalty = (int) Math.min(30,
                        Math.round(accessibility.imagesMissingAlt() * 30.0 / accessibility.totalImages()));
                score -= penalty;
                notes.add("-" + penalty + ": " + accessibility.imagesMissingAlt() + " of "
                        + accessibility.totalImages() + " images missing alt text");
            }
            if (accessibility.totalInputs() != null && accessibility.totalInputs() > 0
                    && accessibility.inputsMissingLabel() != null && accessibility.inputsMissingLabel() > 0) {
                int penalty = (int) Math.min(20,
                        Math.round(accessibility.inputsMissingLabel() * 20.0 / accessibility.totalInputs()));
                score -= penalty;
                notes.add("-" + penalty + ": " + accessibility.inputsMissingLabel()
                        + " form inputs without a discoverable label");
            }
            if (accessibility.emptyLinkCount() != null && accessibility.emptyLinkCount() > 0) {
                int penalty = Math.min(15, accessibility.emptyLinkCount() * 3);
                score -= penalty;
                notes.add("-" + penalty + ": " + accessibility.emptyLinkCount() + " links with no accessible name");
            }
            if (accessibility.emptyButtonCount() != null && accessibility.emptyButtonCount() > 0) {
                int penalty = Math.min(10, accessibility.emptyButtonCount() * 3);
                score -= penalty;
                notes.add("-" + penalty + ": " + accessibility.emptyButtonCount()
                        + " buttons with no accessible name");
            }
            if (!Boolean.TRUE.equals(accessibility.hasPageTitle())) {
                score -= 10;
                notes.add("-10: the page has no title");
            }
            accessibilityScore = clampScore(score);
            notes.add(0, "Automatable structural checks only — this is not a WCAG conformance score. Final: "
                    + accessibilityScore);
            breakdown.put("accessibility", notes);
        }

        // Best practices
        List<String> bestPracticeNotes = new ArrayList<>();
        int bestPractices = 100;
        if (!https) {
            bestPractices -= 25;
            bestPracticeNotes.add("-25: not served over HTTPS");
        }
        if (page != null && !Boolean.TRUE.equals(page.hasViewportMeta())) {
            bestPractices -= 15;
            bestPracticeNotes.add("-15: no viewport meta tag, so the page will not adapt to mobile screens");
        }
        if (page != null && page.charset() == null) {
            bestPractices -= 8;
            bestPracticeNotes.add("-8: no charset declaration");
        }
        if (redirectCount > 2) {
            int penalty = Math.min(15, redirectCount * 5);
            bestPractices -= penalty;
            bestPracticeNotes.add("-" + penalty + ": " + redirectCount
                    + " redirects before the final page, each adding a round-trip");
        }
        if (page != null && page.inlineScriptCount() != null && page.inlineScriptCount() > 10) {
            bestPractices -= 8;
            bestPracticeNotes.add("-8: " + page.inlineScriptCount() + " inline <script> blocks, which "
                    + "complicate a strict Content-Security-Policy");
        }
        if (statusCode >= 400) {
            bestPractices -= 40;
            bestPracticeNotes.add("-40: the page itself returned HTTP " + statusCode);
        }
        bestPractices = clampScore(bestPractices);
        bestPracticeNotes.add(0, "Starts at 100; points are deducted per observed defect. Final: " + bestPractices);
        breakdown.put("bestPractices", bestPracticeNotes);

        // Performance — server response time only, explicitly not a rendered-page score.
        Integer performance = null;
        if (responseTimeMs != null) {
            List<String> notes = new ArrayList<>();
            int score;
            if (responseTimeMs <= 200) {
                score = 100;
            } else if (responseTimeMs <= 500) {
                score = 90;
            } else if (responseTimeMs <= 1000) {
                score = 75;
            } else if (responseTimeMs <= 2000) {
                score = 55;
            } else if (responseTimeMs <= 4000) {
                score = 35;
            } else {
                score = 15;
            }
            notes.add("Derived solely from the measured " + responseTimeMs
                    + "ms time-to-last-byte for the initial HTML document. This is a server-response metric, "
                    + "not a rendered-page score — it says nothing about LCP, CLS or INP, which need a real "
                    + "browser. Final: " + score);
            performance = score;
            breakdown.put("performance", notes);
        } else {
            unavailable.add("Performance score - not available: the response time could not be measured.");
        }

        return new Scores(security, seoScore, accessibilityScore, bestPractices, performance, breakdown);
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    // ─── Recommendations, derived from the same observations ─────────────────────

    private List<Recommendation> buildRecommendations(List<HeaderCheck> securityHeaders, SeoChecks seo,
                                                     AccessibilityChecks accessibility, PageMetadata page,
                                                     Long responseTimeMs, boolean https, TlsInfo tls,
                                                     List<CookieCheck> cookies, List<LinkCheck> links,
                                                     List<RedirectHop> redirects) {
        List<Recommendation> recommendations = new ArrayList<>();

        if (!https) {
            recommendations.add(new Recommendation("SECURITY", "CRITICAL", "Serve the site over HTTPS",
                    "The final URL resolved to plain HTTP. All traffic, including any credentials or session "
                            + "cookies, can be read and modified in transit. Obtain a certificate and redirect "
                            + "HTTP to HTTPS permanently."));
        }
        if (tls != null && tls.daysUntilExpiry() != null && tls.daysUntilExpiry() < 30) {
            recommendations.add(new Recommendation("SECURITY",
                    tls.daysUntilExpiry() < 0 ? "CRITICAL" : "HIGH",
                    "TLS certificate " + (tls.daysUntilExpiry() < 0 ? "has expired" : "expires soon"),
                    "The certificate " + (tls.daysUntilExpiry() < 0
                            ? "expired " + Math.abs(tls.daysUntilExpiry()) + " day(s) ago, so browsers are "
                                    + "showing a security warning."
                            : "expires in " + tls.daysUntilExpiry() + " day(s).")
                            + " Automate renewal so this cannot lapse."));
        }
        for (HeaderCheck header : securityHeaders) {
            if (!header.present() && ("HIGH".equals(header.severity()) || "MEDIUM".equals(header.severity()))) {
                recommendations.add(new Recommendation("SECURITY", header.severity(),
                        "Add the " + header.name() + " header", header.assessment()));
            }
        }
        cookies.stream()
                .filter(c -> !c.secure() || !c.httpOnly())
                .findFirst()
                .ifPresent(cookie -> recommendations.add(new Recommendation("SECURITY", "MEDIUM",
                        "Harden cookie attributes",
                        "Cookie \"" + cookie.name() + "\" is set without full protection. " + cookie.assessment()
                                + " Set Secure, HttpOnly and an explicit SameSite value on all session cookies.")));

        if (responseTimeMs != null && responseTimeMs > 1000) {
            recommendations.add(new Recommendation("PERFORMANCE",
                    responseTimeMs > 3000 ? "HIGH" : "MEDIUM",
                    "Reduce server response time",
                    "The initial HTML document took a measured " + responseTimeMs + "ms to arrive. Under 200ms "
                            + "is the usual target. Investigate slow queries and un-cached work on the request "
                            + "path, and consider a CDN or edge cache for this document."));
        }
        if (redirects.size() > 2) {
            recommendations.add(new Recommendation("PERFORMANCE", "LOW", "Shorten the redirect chain",
                    "The request passed through " + redirects.size() + " redirects before reaching the final "
                            + "page. Each adds a full round-trip. Point the first URL at the final destination."));
        }

        long brokenLinks = links.stream().filter(LinkCheck::broken).count();
        if (brokenLinks > 0) {
            String examples = links.stream().filter(LinkCheck::broken).limit(3)
                    .map(l -> l.url() + " (" + (l.statusCode() != null ? "HTTP " + l.statusCode() : l.error()) + ")")
                    .reduce((a, b) -> a + "; " + b).orElse("");
            recommendations.add(new Recommendation("QUALITY", "MEDIUM",
                    brokenLinks + " broken link(s) found",
                    "These links were requested and did not resolve successfully: " + examples
                            + ". Broken links harm both users and search ranking."));
        }

        if (seo != null) {
            seo.issues().stream().limit(4).forEach(issue ->
                    recommendations.add(new Recommendation("SEO", "LOW", "SEO improvement", issue)));
        }
        if (accessibility != null) {
            accessibility.issues().stream().limit(4).forEach(issue ->
                    recommendations.add(new Recommendation("ACCESSIBILITY", "MEDIUM",
                            "Accessibility improvement", issue)));
        }
        if (page != null && !Boolean.TRUE.equals(page.hasViewportMeta())) {
            recommendations.add(new Recommendation("BEST_PRACTICES", "MEDIUM", "Add a viewport meta tag",
                    "Without <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"> mobile "
                            + "browsers render the page at desktop width and scale it down."));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(new Recommendation("QUALITY", "INFO", "No issues found by the checks performed",
                    "Every check QPilot was able to run passed. Note the \"not available\" list for what was "
                            + "not assessed — rendered-page performance and full WCAG conformance in particular."));
        }
        return recommendations;
    }

    // ─── Small helpers ───────────────────────────────────────────────────────────

    private URI validateUrl(String rawUrl) {
        String candidate = rawUrl.trim();
        if (candidate.isEmpty()) {
            throw new BadRequestException("A target URL is required.");
        }
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("\"" + rawUrl + "\" is not a complete URL — it has no host. "
                        + "Try something like https://example.com");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new BadRequestException("\"" + rawUrl + "\" is not a valid URL: " + e.getMessage());
        }
    }

    private String describeStatus(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> statusCode >= 200 && statusCode < 300 ? "Success"
                    : statusCode >= 300 && statusCode < 400 ? "Redirect"
                    : statusCode >= 400 && statusCode < 500 ? "Client Error" : "Server Error";
        };
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private List<String> allGroups(Pattern pattern, String text) {
        List<String> groups = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && groups.size() < 50) {
            groups.add(matcher.group(1));
        }
        return groups;
    }

    private int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
