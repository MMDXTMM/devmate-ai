package com.devmate.knowledge.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Recognizes code targets without guessing a natural-language domain translation. */
public final class RetrievalQueryTargets {
    private static final Pattern TARGET = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$-]*(?:[.#/][A-Za-z_$][A-Za-z0-9_$-]*)+"
                    + "|[a-z][a-z0-9]*[A-Z][A-Za-z0-9_$]*|[A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]+|`[A-Za-z_$][A-Za-z0-9_$]*`");
    private RetrievalQueryTargets() { }
    public static List<String> identifiers(String query) {
        Set<String> found = new LinkedHashSet<>();
        var matcher = TARGET.matcher(query == null ? "" : query);
        while (matcher.find() && found.size() < 12) found.add(matcher.group().replace("`", ""));
        if (found.isEmpty() && query != null && query.matches("[A-Za-z_$][A-Za-z0-9_$]{1,100}")) found.add(query);
        return List.copyOf(found);
    }
    public static boolean hasExplicitTarget(String query) { return !identifiers(query).isEmpty(); }
}
