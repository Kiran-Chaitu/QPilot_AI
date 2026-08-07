package com.testforge.backend.project.service;

import com.testforge.backend.common.util.IgnoredPaths;
import com.testforge.backend.project.dto.ApiEndpointSummary;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lightweight, dependency-free static analysis of an extracted project:
 * language detection, dependency-manifest parsing and API-endpoint discovery
 * via regex over source files. This stands in for a full parser/RAG pipeline,
 * which is out of scope for the hackathon MVP (see PROJECT_PLAN.md).
 */
@Service
public class ProjectMetadataAnalyzer {

    // Shared with FileStorageService's zip extraction so the ignore list is defined once.

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("py", "Python"), Map.entry("go", "Go"), Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"), Map.entry("cs", "C#"), Map.entry("cpp", "C++"),
            Map.entry("c", "C"), Map.entry("rs", "Rust"), Map.entry("html", "HTML"),
            Map.entry("css", "CSS"), Map.entry("sql", "SQL"), Map.entry("yml", "YAML"),
            Map.entry("yaml", "YAML"), Map.entry("json", "JSON"), Map.entry("xml", "XML"),
            Map.entry("dart", "Dart"), Map.entry("swift", "Swift"), Map.entry("scala", "Scala"),
            Map.entry("m", "Objective-C"), Map.entry("sh", "Shell")
    );

    // Extensions treated as "real" application source code for the purposes of picking
    // AI-prompt excerpts. Kept separate from EXTENSION_TO_LANGUAGE (which also includes
    // config/markup formats like XML/JSON/YAML that shouldn't be used as key-file excerpts).
    private static final Set<String> SOURCE_CODE_EXTENSIONS =
            Set.of("java", "kt", "ts", "js", "py", "dart", "swift", "go", "rb", "php", "cs", "cpp", "c", "rs");

    // Spring MVC style: @GetMapping("/path") or @RequestMapping(value="/path", method=RequestMethod.POST).
    // Bare @RequestMapping with no explicit method is usually a class-level base path rather than
    // an actual endpoint, so it's handled separately below instead of being reported as-is.
    private static final Pattern SPRING_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(([^)]*)\\)");
    private static final Pattern SPRING_MAPPING_PATH = Pattern.compile("(?:value\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern SPRING_MAPPING_METHOD = Pattern.compile("method\\s*=\\s*RequestMethod\\.(\\w+)");
    // Express/Node style: app.get('/path', ...), router.post("/path", ...)
    private static final Pattern EXPRESS_MAPPING = Pattern.compile(
            "(?:app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]*)['\"]");
    // Flask/Python style: @app.route('/path', methods=['POST'])
    private static final Pattern FLASK_MAPPING = Pattern.compile(
            "@(?:app|blueprint|bp)\\.route\\s*\\(\\s*['\"]([^'\"]*)['\"](?:[^)]*methods\\s*=\\s*\\[([^\\]]*)])?");

    private static final int MAX_KEY_FILES = 8;
    private static final int MAX_EXCERPT_CHARS = 2500;
    private static final long MAX_FILES_TO_SCAN = 5000;

    public ProjectStructureSummary analyze(Path projectRoot) {
        List<Path> allFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(projectRoot, p))
                    .limit(MAX_FILES_TO_SCAN)
                    .forEach(allFiles::add);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk project directory: " + e.getMessage(), e);
        }

        Map<String, Long> languageBreakdown = allFiles.stream()
                .map(this::extensionOf)
                .filter(EXTENSION_TO_LANGUAGE::containsKey)
                .map(EXTENSION_TO_LANGUAGE::get)
                .collect(Collectors.groupingBy(l -> l, Collectors.counting()));

        String primaryLanguage = languageBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        List<String> dependencies = extractDependencies(projectRoot);
        List<ApiEndpointSummary> endpoints = discoverEndpoints(projectRoot, allFiles);
        List<String> topLevelEntries = listTopLevel(projectRoot);
        List<ProjectStructureSummary.KeyFile> keyFiles = pickKeyFiles(projectRoot, allFiles, endpoints);

        return new ProjectStructureSummary(
                allFiles.size(), languageBreakdown, primaryLanguage, dependencies, endpoints, topLevelEntries, keyFiles);
    }

    private boolean isIgnored(Path root, Path file) {
        Path rel = root.relativize(file);
        for (Path part : rel) {
            if (IgnoredPaths.IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> extractDependencies(Path root) {
        List<String> deps = new ArrayList<>();
        try {
            findFirst(root, "pom.xml").ifPresent(p -> deps.addAll(parsePomDependencies(p)));
            findFirst(root, "package.json").ifPresent(p -> deps.addAll(parsePackageJsonDependencies(p)));
            findFirst(root, "requirements.txt").ifPresent(p -> deps.addAll(parseRequirementsTxt(p)));
            findFirst(root, "build.gradle").ifPresent(p -> deps.addAll(parseGradleDependencies(p)));
        } catch (IOException e) {
            // best-effort; analysis should not fail the whole upload because a manifest is malformed
        }
        return deps.stream().distinct().limit(200).collect(Collectors.toList());
    }

    private Optional<Path> findFirst(Path root, String fileName) throws IOException {
        try (Stream<Path> walk = Files.walk(root, 4)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(root, p))
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();
        }
    }

    private List<String> parsePomDependencies(Path pom) {
        List<String> result = new ArrayList<>();
        try {
            String content = Files.readString(pom, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>")
                    .matcher(content);
            while (m.find()) {
                result.add(m.group(1) + ":" + m.group(2));
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private List<String> parsePackageJsonDependencies(Path packageJson) {
        List<String> result = new ArrayList<>();
        try {
            String content = Files.readString(packageJson, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"([a-zA-Z0-9@/_.-]+)\"\\s*:\\s*\"[^\"]*\"").matcher(content);
            boolean inDeps = false;
            for (String line : content.split("\n")) {
                if (line.contains("\"dependencies\"") || line.contains("\"devDependencies\"")) {
                    inDeps = true;
                    continue;
                }
                if (inDeps && line.trim().equals("}") || line.trim().equals("},")) {
                    inDeps = false;
                    continue;
                }
                if (inDeps) {
                    Matcher lm = Pattern.compile("\"([a-zA-Z0-9@/_.-]+)\"\\s*:").matcher(line);
                    if (lm.find()) {
                        result.add(lm.group(1));
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private List<String> parseRequirementsTxt(Path req) {
        try {
            return Files.readAllLines(req, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> parseGradleDependencies(Path gradle) {
        List<String> result = new ArrayList<>();
        try {
            String content = Files.readString(gradle, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?:implementation|api|compile|testImplementation)\\s*['\"]([^'\"]+)['\"]")
                    .matcher(content);
            while (m.find()) {
                result.add(m.group(1));
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private List<ApiEndpointSummary> discoverEndpoints(Path root, List<Path> files) {
        List<ApiEndpointSummary> endpoints = new ArrayList<>();
        for (Path file : files) {
            String ext = extensionOf(file);
            if (!Set.of("java", "kt", "js", "ts", "py").contains(ext)) {
                continue;
            }
            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            String relative = root.relativize(file).toString().replace('\\', '/');

            if (ext.equals("java") || ext.equals("kt")) {
                Matcher m = SPRING_MAPPING.matcher(content);
                while (m.find()) {
                    String annotation = m.group(1);
                    String args = m.group(2);
                    Matcher methodMatcher = SPRING_MAPPING_METHOD.matcher(args);
                    String method;
                    if (annotation.equalsIgnoreCase("Request")) {
                        // Bare @RequestMapping only counts as an endpoint if it explicitly
                        // declares an HTTP method; otherwise it's typically a class-level base
                        // path prefix rather than a real handler.
                        if (!methodMatcher.find()) {
                            continue;
                        }
                        method = methodMatcher.group(1).toUpperCase(Locale.ROOT);
                    } else {
                        method = annotation.toUpperCase(Locale.ROOT);
                    }
                    Matcher pathMatcher = SPRING_MAPPING_PATH.matcher(args);
                    String path = pathMatcher.find() ? pathMatcher.group(1) : "/";
                    endpoints.add(new ApiEndpointSummary(method, path, relative, file.getFileName().toString()));
                }
            } else if (ext.equals("js") || ext.equals("ts")) {
                Matcher m = EXPRESS_MAPPING.matcher(content);
                while (m.find()) {
                    endpoints.add(new ApiEndpointSummary(m.group(1).toUpperCase(Locale.ROOT), m.group(2), relative, file.getFileName().toString()));
                }
            } else if (ext.equals("py")) {
                Matcher m = FLASK_MAPPING.matcher(content);
                while (m.find()) {
                    String methods = m.group(2) != null ? m.group(2).replaceAll("['\"\\s]", "") : "GET";
                    endpoints.add(new ApiEndpointSummary(methods, m.group(1), relative, file.getFileName().toString()));
                }
            }
            if (endpoints.size() > 500) {
                break;
            }
        }
        return endpoints;
    }

    private List<String> listTopLevel(Path root) {
        try (Stream<Path> list = Files.list(root)) {
            return list.map(p -> p.getFileName().toString())
                    .sorted()
                    .limit(100)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Chooses a handful of the most relevant source files (controllers/services with
     * discovered endpoints first, then anything named "Service" or "Controller") to
     * include as excerpts in AI prompts, capped to keep total context small.
     */
    private List<ProjectStructureSummary.KeyFile> pickKeyFiles(Path root, List<Path> files, List<ApiEndpointSummary> endpoints) {
        Set<String> endpointFiles = endpoints.stream().map(ApiEndpointSummary::sourceFile).collect(Collectors.toSet());

        List<Path> candidates = files.stream()
                .filter(p -> SOURCE_CODE_EXTENSIONS.contains(extensionOf(p)))
                .sorted(Comparator.comparing((Path p) -> {
                    String name = p.getFileName().toString();
                    boolean hasEndpoint = endpointFiles.contains(name);
                    // Case-insensitive so snake_case files (e.g. Dart/Python's "auth_service.dart")
                    // are recognized as readily as PascalCase ones (e.g. Java's "AuthService.java").
                    boolean looksImportant = name.toLowerCase(Locale.ROOT)
                            .matches(".*(service|controller|handler|resource|routes?|provider|repository|widget).*");
                    if (hasEndpoint) return 0;
                    if (looksImportant) return 1;
                    return 2;
                }))
                .limit(MAX_KEY_FILES)
                .collect(Collectors.toList());

        List<ProjectStructureSummary.KeyFile> result = new ArrayList<>();
        for (Path p : candidates) {
            try {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                String excerpt = content.length() > MAX_EXCERPT_CHARS ? content.substring(0, MAX_EXCERPT_CHARS) + "\n/* ...truncated... */" : content;
                result.add(new ProjectStructureSummary.KeyFile(root.relativize(p).toString().replace('\\', '/'), excerpt));
            } catch (IOException ignored) {
            }
        }
        return result;
    }
}
