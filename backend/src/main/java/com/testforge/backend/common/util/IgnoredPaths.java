package com.testforge.backend.common.util;

import java.util.Set;

/**
 * Directory names that are near-universally build artifacts, dependency caches or VCS metadata
 * across the languages/frameworks QPilot AI supports (Java/Kotlin, Node/React/Angular/Vue,
 * Python, Go, Rust, Ruby, PHP/Laravel, .NET, Flutter/Dart, Swift/iOS, etc). Shared by both the
 * zip-extraction pipeline (skip entirely while extracting, saving disk and time on real-world
 * archives that bundle gigabytes of build output) and the metadata analyzer (skip while walking
 * already-extracted files, as a safety net for archives that weren't extracted through this
 * pipeline or that partially contain such directories at nested levels).
 */
public final class IgnoredPaths {

    public static final Set<String> IGNORED_DIR_NAMES = Set.of(
            // VCS / editor metadata
            ".git", ".svn", ".hg", ".idea", ".vscode", ".vs",
            // Node / JS / TS ecosystems
            "node_modules", "bower_components", ".next", ".nuxt", ".turbo",
            // JVM build tools
            "target", "build", ".gradle", ".mvn",
            // Generic build/dist output
            "dist", "out", "bin", "obj",
            // Python
            "venv", ".venv", "env", "__pycache__", ".pytest_cache", ".mypy_cache",
            // Flutter/Dart
            ".dart_tool", ".fvm", ".pub-cache",
            // iOS/macOS
            "Pods", "DerivedData", ".build",
            // Test/coverage output
            "coverage", ".nyc_output",
            // PHP/Go dependency vendoring (re-fetchable, not hand-authored source)
            "vendor"
    );

    private IgnoredPaths() {
    }

    /** True if any path segment of the given (already '/'-normalized) relative path is an ignored directory name. */
    public static boolean containsIgnoredSegment(String normalizedRelativePath) {
        for (String segment : normalizedRelativePath.split("/")) {
            if (IGNORED_DIR_NAMES.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}
