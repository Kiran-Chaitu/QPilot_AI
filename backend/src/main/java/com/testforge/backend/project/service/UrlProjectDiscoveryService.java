package com.testforge.backend.project.service;

import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerEndpointSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers what a URL-based project actually exposes, by asking it.
 *
 * <p>This replaces a hardcoded structure summary that every URL project used to receive: a fixed file
 * count of 1, an invented dependency list ("Spring Boot, Jackson, JUnit 5"), and two fictional endpoints
 * — {@code GET /api/v1/health} and {@code POST /api/v1/data}. Those fabrications were then fed to the AI
 * as project context and used to generate tests, so a user pointing QPilot at their real API received
 * tests for routes that did not exist while their actual routes went untested.
 *
 * <p>Discovery now works from real evidence, in order of reliability:
 * <ol>
 *   <li><b>OpenAPI document</b> — probed at the conventional locations. When found, its routes, methods
 *       and parameters are authoritative.</li>
 *   <li><b>Live HTML</b> — the page is fetched and its real technology fingerprints and same-origin
 *       links are recorded.</li>
 * </ol>
 *
 * <p>When neither yields anything, the structure is returned honestly empty with a note explaining what
 * was tried. An empty result the user can act on beats a populated one they cannot trust.
 */
@Service
public class UrlProjectDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(UrlProjectDiscoveryService.class);

    private static final String USER_AGENT = "QPilot-AI-Discovery/1.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Conventional locations an OpenAPI document is published at, most common first. */
    private static final List<String> OPENAPI_PROBE_PATHS = List.of(
            "/v3/api-docs", "/v3/api-docs.yaml", "/openapi.json", "/openapi.yaml",
            "/swagger.json", "/swagger.yaml", "/api-docs", "/api/openapi.json",
            "/api/v1/openapi.json", "/docs/openapi.json", "/swagger/v1/swagger.json");

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANCHOR_HREF = Pattern.compile("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public UrlProjectDiscoveryService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    /** Everything discovery managed to establish, plus notes on what it could not. */
    public record DiscoveryResult(
            ProjectStructureSummary structure,
            SwaggerParseResult swagger,
            String detectedTitle,
            String primaryLanguageLabel,
            List<String> notes,
            boolean reachable
    ) {
    }

    public DiscoveryResult discover(String websiteUrl, String apiBaseUrl) {
        List<String> notes = new ArrayList<>();
        String probeBase = firstNonBlank(apiBaseUrl, websiteUrl);
        if (probeBase == null) {
            notes.add("No URL was supplied, so nothing could be discovered.");
            return new DiscoveryResult(emptyStructure(notes), null, null, "Unknown", notes, false);
        }

        URI base = normalize(probeBase);

        // 1. OpenAPI first: a machine-readable contract beats anything inferred from HTML.
        SwaggerParseResult swagger = probeForOpenApi(base, notes);
        List<ApiEndpointSummary> endpoints = new ArrayList<>();
        if (swagger != null) {
            for (SwaggerEndpointSummary endpoint : swagger.endpoints()) {
                endpoints.add(new ApiEndpointSummary(endpoint.httpMethod(), endpoint.path(),
                        "OpenAPI: " + swagger.title(),
                        endpoint.operationId() != null ? endpoint.operationId() : endpoint.summary()));
            }
            // The version string frequently already starts with "v", so prefixing another one produced
            // "vv1" in the notes shown to users.
            String version = swagger.version() != null && swagger.version().toLowerCase(Locale.ROOT).startsWith("v")
                    ? swagger.version() : "v" + swagger.version();
            notes.add("Discovered " + endpoints.size() + " endpoint(s) from the target's own OpenAPI document ("
                    + swagger.title() + " " + version + ").");
        }

        // 2. Fetch the page itself for real technology and link evidence.
        PageProbe page = probePage(websiteUrl != null && !websiteUrl.isBlank() ? normalize(websiteUrl) : base, notes);

        if (endpoints.isEmpty()) {
            notes.add("No API endpoints could be discovered. QPilot probed these conventional OpenAPI locations "
                    + "and none returned a parseable document: " + String.join(", ", OPENAPI_PROBE_PATHS)
                    + ". Upload an OpenAPI/Swagger file to the project, or upload the source archive, to enable "
                    + "endpoint-level test generation.");
        }

        Map<String, Long> languageBreakdown = new LinkedHashMap<>();
        String primaryLanguage;
        if (swagger != null) {
            primaryLanguage = "REST API (OpenAPI)";
            languageBreakdown.put("OpenAPI endpoints", (long) endpoints.size());
        } else if (page.reachable()) {
            primaryLanguage = "Live web target";
            languageBreakdown.put("HTML documents", 1L);
        } else {
            primaryLanguage = "Unknown";
        }

        ProjectStructureSummary structure = new ProjectStructureSummary(
                // File counts are zero and stay zero: nothing was downloaded, so claiming a file count
                // would be inventing one. Source metrics require an uploaded archive.
                0,
                languageBreakdown,
                primaryLanguage,
                // No dependency manifest is reachable over HTTP, so the list is genuinely empty.
                List.of(),
                endpoints,
                page.discoveredPaths(),
                List.of());

        notes.add("Source-level metrics (file counts, lines of code, dependency list, code-level security "
                + "scanning) are not available for a URL-based project — they require the source archive. "
                + "Upload a ZIP of the project to enable them.");

        return new DiscoveryResult(structure, swagger, page.title(), primaryLanguage, notes,
                page.reachable() || swagger != null);
    }

    // ─── OpenAPI probing ─────────────────────────────────────────────────────────

    private SwaggerParseResult probeForOpenApi(URI base, List<String> notes) {
        // The supplied URL may itself be the document (the OpenAPI import flow asks for exactly that),
        // so try it directly first. Skipping this step meant a direct spec URL was never read — every
        // probe appended a conventional suffix to it and 404'd.
        SwaggerParseResult direct = tryFetchOpenApi(base, notes);
        if (direct != null) {
            return direct;
        }
        for (String path : OPENAPI_PROBE_PATHS) {
            SwaggerParseResult parsed = tryFetchOpenApi(base.resolve(path), notes);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /** Fetches one candidate URL and parses it as OpenAPI, returning null if it is not a usable document. */
    private SwaggerParseResult tryFetchOpenApi(URI candidate, List<String> notes) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(candidate)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, application/yaml, text/yaml, */*")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            SwaggerParseResult parsed = parseOpenApiContent(response.body());
            if (parsed != null && !parsed.endpoints().isEmpty()) {
                log.info("Discovered OpenAPI document at {} with {} endpoints", candidate, parsed.endpoints().size());
                notes.add("OpenAPI document read from " + candidate + ".");
                return parsed;
            }
            return null;
        } catch (Exception e) {
            // Probing a location that does not exist is the expected case, not an error worth surfacing.
            log.trace("OpenAPI probe of {} failed: {}", candidate, e.getMessage());
            return null;
        }
    }

    /** Parses an OpenAPI document from its raw text (JSON or YAML), returning null if it is not one. */
    private SwaggerParseResult parseOpenApiContent(String content) {
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(false); // no remote $ref resolution: that would fetch arbitrary URLs
            io.swagger.v3.parser.core.models.SwaggerParseResult result =
                    new OpenAPIV3Parser().readContents(content, null, options);
            OpenAPI openAPI = result.getOpenAPI();
            if (openAPI == null || openAPI.getPaths() == null) {
                return null;
            }

            List<SwaggerEndpointSummary> endpoints = new ArrayList<>();
            openAPI.getPaths().forEach((path, pathItem) -> {
                Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
                operations.forEach((method, operation) -> {
                    List<String> params = operation.getParameters() == null ? List.of()
                            : operation.getParameters().stream()
                                    .map(p -> p.getName() + " (" + p.getIn()
                                            + (Boolean.TRUE.equals(p.getRequired()) ? ", required" : "") + ")")
                                    .toList();
                    String requestBody = operation.getRequestBody() != null
                            && operation.getRequestBody().getContent() != null
                            ? String.join(",", operation.getRequestBody().getContent().keySet()) : null;
                    List<String> responses = operation.getResponses() == null ? List.of()
                            : new ArrayList<>(operation.getResponses().keySet());
                    endpoints.add(new SwaggerEndpointSummary(method.name(), path, operation.getOperationId(),
                            operation.getSummary(), params, requestBody, responses));
                });
            });

            String title = openAPI.getInfo() != null && openAPI.getInfo().getTitle() != null
                    ? openAPI.getInfo().getTitle() : "Discovered API";
            String version = openAPI.getInfo() != null && openAPI.getInfo().getVersion() != null
                    ? openAPI.getInfo().getVersion() : "unspecified";
            return new SwaggerParseResult(title, version, endpoints);

        } catch (Exception e) {
            return null;
        }
    }

    // ─── Page probing ────────────────────────────────────────────────────────────

    private record PageProbe(boolean reachable, String title, List<String> discoveredPaths) {
    }

    private PageProbe probePage(URI url, List<String> notes) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() != null ? response.body() : "";

            if (response.statusCode() >= 400) {
                notes.add("The target responded with HTTP " + response.statusCode()
                        + ", so no page structure could be read from it.");
                return new PageProbe(true, null, List.of());
            }

            String title = null;
            Matcher titleMatcher = TITLE.matcher(body);
            if (titleMatcher.find()) {
                title = titleMatcher.group(1).replaceAll("<[^>]+>", "").trim();
            }

            // Same-origin paths only. Recording third-party URLs as part of "this project's structure"
            // would misattribute someone else's site to the user's project.
            Set<String> paths = new LinkedHashSet<>();
            collectSameOriginPaths(ANCHOR_HREF, body, url, paths);
            collectSameOriginPaths(SCRIPT_SRC, body, url, paths);

            notes.add("Fetched the target page successfully (HTTP " + response.statusCode() + ")"
                    + (title != null ? " — page title: \"" + title + "\"" : "")
                    + ". " + paths.size() + " same-origin path(s) were observed in its markup.");

            return new PageProbe(true, title, new ArrayList<>(paths).subList(0, Math.min(paths.size(), 50)));

        } catch (Exception e) {
            notes.add("The target could not be fetched: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " — " + e.getMessage() : "")
                    + ". The project was still created so you can correct the URL, but no structure was discovered.");
            return new PageProbe(false, null, List.of());
        }
    }

    private void collectSameOriginPaths(Pattern pattern, String html, URI base, Set<String> sink) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find() && sink.size() < 50) {
            String raw = matcher.group(1).trim();
            if (raw.startsWith("#") || raw.startsWith("mailto:") || raw.startsWith("tel:")
                    || raw.startsWith("javascript:") || raw.startsWith("data:")) {
                continue;
            }
            try {
                URI resolved = base.resolve(raw);
                if (resolved.getHost() != null && resolved.getHost().equalsIgnoreCase(base.getHost())) {
                    String path = resolved.getPath();
                    if (path != null && !path.isBlank() && !path.equals("/")) {
                        sink.add(path);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Unparseable reference: skipped rather than recorded as a discovered path.
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private ProjectStructureSummary emptyStructure(List<String> notes) {
        return new ProjectStructureSummary(0, Map.of(), "Unknown", List.of(), List.of(), List.of(), List.of());
    }

    private URI normalize(String rawUrl) {
        String candidate = rawUrl.trim();
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        // A base URI without a trailing slash makes resolve("/path") drop the last path segment, which
        // would silently probe the wrong location for API bases such as https://host/api.
        if (!candidate.endsWith("/")) {
            candidate = candidate + "/";
        }
        return URI.create(candidate);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
