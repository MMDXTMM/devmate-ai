package com.devmate.knowledge.source;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Choose the repository introduction before nested package documentation, without reading live files. */
public final class ProjectReadme {
    private ProjectReadme() { }

    public static boolean isReadme(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return name.equals("readme") || name.matches("readme(?:[._-][a-z0-9_-]+)*\\.(md|markdown|rst|txt|adoc)");
    }

    public static Optional<String> primary(List<String> paths) {
        return paths.stream().filter(ProjectReadme::isReadme).min(
                Comparator.comparingLong((String path) -> path.chars().filter(c -> c == '/').count())
                        .thenComparingInt(ProjectReadme::formatPriority)
                        .thenComparing(path -> path.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()));
    }

    private static int formatPriority(String path) {
        return switch (path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT)) {
            case "readme.md" -> 0;
            case "readme" -> 1;
            case "readme.rst" -> 2;
            case "readme.markdown" -> 3;
            case "readme.txt" -> 4;
            case "readme.adoc" -> 5;
            default -> 6;
        };
    }
}
