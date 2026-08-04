package com.devmate.knowledge.source;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum SourceFileType {
    JAVA,
    YAML,
    PROPERTIES;

    public static Optional<SourceFileType> from(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return Optional.of(JAVA);
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return Optional.of(YAML);
        }
        if (name.endsWith(".properties")) {
            return Optional.of(PROPERTIES);
        }
        return Optional.empty();
    }
}
