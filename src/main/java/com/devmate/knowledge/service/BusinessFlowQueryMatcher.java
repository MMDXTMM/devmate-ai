package com.devmate.knowledge.service;

import com.devmate.knowledge.dto.ProjectUnderstandingFlowResponse;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Uses the project's saved business vocabulary; no hard-coded domain translations. */
final class BusinessFlowQueryMatcher {
    private static final Pattern TERMS = Pattern.compile("[\\p{IsHan}]{2,}|[a-zA-Z][a-zA-Z0-9_]{1,}");
    private static final Set<String> GENERIC = Set.of("项目", "业务", "流程", "代码", "实现", "接口",
            "哪些", "如何", "怎么", "系统", "说明", "步骤", "核心", "当前", "这个", "功能", "主要", "处理");

    private BusinessFlowQueryMatcher() { }

    static List<ProjectUnderstandingFlowResponse> match(String query, List<ProjectUnderstandingFlowResponse> flows) {
        Set<String> terms = terms(query);
        List<ProjectUnderstandingFlowResponse> matched = flows.stream()
                .filter(flow -> score(terms, flow) > 0)
                .sorted(Comparator.comparingInt((ProjectUnderstandingFlowResponse flow) -> score(terms, flow)).reversed())
                .limit(2).toList();
        if (!matched.isEmpty()) return matched;
        // An overview gets representative code from each known flow, never invented features.
        return query.matches("(?s).*(有哪些功能|整体|概览|主要业务|介绍.*项目|项目.*介绍).*") ? flows : List.of();
    }

    private static int score(Set<String> queryTerms, ProjectUnderstandingFlowResponse flow) {
        Set<String> vocabulary = terms(flow.name() + " " + String.join(" ", flow.apiEntries()));
        return (int) queryTerms.stream().filter(vocabulary::contains).count();
    }

    private static Set<String> terms(String text) {
        Set<String> result = new LinkedHashSet<>();
        var matcher = TERMS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.codePoints().allMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i < word.length() - 1; i++) result.add(word.substring(i, i + 2));
            } else result.add(word);
        }
        result.removeAll(GENERIC);
        return result;
    }
}
