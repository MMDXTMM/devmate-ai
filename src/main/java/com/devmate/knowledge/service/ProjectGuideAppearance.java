package com.devmate.knowledge.service;

import com.devmate.knowledge.dto.ProjectGuide.Appearance;
import com.devmate.knowledge.dto.ProjectGuide.ModuleIcon;
import java.util.LinkedHashMap;
import java.util.Set;

/** Presentation failures must not discard an otherwise valid business report. */
public final class ProjectGuideAppearance {
    private ProjectGuideAppearance() { }

    public static String safeIcon(String value) {
        if (value == null || value.length() > 32 || !value.matches("\\X")) return null;
        boolean pictograph = value.codePoints().anyMatch(cp -> cp >= 0x1F000 && cp <= 0x1FAFF
                || cp >= 0x2600 && cp <= 0x27BF || cp >= 0x2300 && cp <= 0x23FF);
        return pictograph ? value : null;
    }

    public static Appearance sanitize(Appearance input, Set<String> moduleIds) {
        if (input == null) return null;
        var icons = new LinkedHashMap<String, String>();
        if (input.modules() != null) {
            for (var item : input.modules().stream().limit(20).toList()) {
                if (item == null || !moduleIds.contains(item.moduleId())) continue;
                String icon = safeIcon(item.icon());
                if (icon != null) icons.putIfAbsent(item.moduleId(), icon);
            }
        }
        return new Appearance(safeIcon(input.projectIcon()), icons.entrySet().stream()
                .map(entry -> new ModuleIcon(entry.getKey(), entry.getValue())).toList());
    }
}
