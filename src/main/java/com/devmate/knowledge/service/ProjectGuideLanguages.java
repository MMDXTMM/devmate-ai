package com.devmate.knowledge.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Host-observed source suffixes, not dependency or model guesses; no percentage/primary-language claims. */
public final class ProjectGuideLanguages {
    private static final Map<String, String> SUFFIXES = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("py", "Python"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("cjs", "JavaScript"), Map.entry("vue", "Vue 组件"),
            Map.entry("svelte", "Svelte 组件"), Map.entry("go", "Go"), Map.entry("rs", "Rust"),
            Map.entry("kt", "Kotlin"), Map.entry("kts", "Kotlin"), Map.entry("scala", "Scala"),
            Map.entry("c", "C"), Map.entry("h", "C/C++ 头文件"), Map.entry("cpp", "C++"),
            Map.entry("hpp", "C/C++ 头文件"), Map.entry("cs", "C#"), Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"), Map.entry("swift", "Swift"), Map.entry("dart", "Dart"),
            Map.entry("sh", "Shell"), Map.entry("bash", "Shell"), Map.entry("html", "HTML"),
            Map.entry("css", "CSS"), Map.entry("scss", "SCSS"), Map.entry("sql", "SQL")
    );
    private ProjectGuideLanguages() { }
    public static List<String> detect(List<String> paths) {
        var result = new TreeSet<String>();
        for (String path : paths) {
            int dot = path.lastIndexOf('.');
            if (dot < path.lastIndexOf('/') || dot < 0) continue;
            String language = SUFFIXES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (language != null) result.add(language);
        }
        return List.copyOf(result);
    }
}
