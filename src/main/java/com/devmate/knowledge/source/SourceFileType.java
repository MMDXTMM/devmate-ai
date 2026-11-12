package com.devmate.knowledge.source;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum SourceFileType {
    JAVA,
    YAML,
    PROPERTIES,
    SQL,
    TEXT,
    DOCUMENTATION,
    MANIFEST;

    public static Optional<SourceFileType> from(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (ProjectReadme.isReadme(name)) return Optional.of(DOCUMENTATION);
        if (java.util.Set.of("package.json", "pom.xml", "pyproject.toml", "requirements.txt",
                "cargo.toml", "go.mod", "build.gradle", "build.gradle.kts", "dockerfile",
                "makefile", "cmakelists.txt", "composer.json", "pubspec.yaml").contains(name)) {
            return Optional.of(MANIFEST);
        }
        if (name.endsWith(".java")) {
            return Optional.of(JAVA);
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return Optional.of(YAML);
        }
        if (name.endsWith(".properties")) {
            return Optional.of(PROPERTIES);
        }
        if (name.endsWith(".sql")) {
            return Optional.of(SQL);
        }
        if (name.matches(".*\\.(md|markdown|mdx|rst|txt|adoc)$")) return Optional.of(DOCUMENTATION);
        if (name.matches(".*\\.(py|ts|tsx|js|jsx|mjs|cjs|vue|svelte|go|rs|kt|kts|scala|groovy|c|h|cpp|hpp|cs|rb|php|swift|dart|sh|bash|html|css|scss|xml|toml|json|tf|proto|graphql|ipynb)$")) {
            return Optional.of(TEXT);
        }
        return Optional.empty();
    }
}
