package com.devmate.knowledge.service;

import com.devmate.agent.model.AiReviewException;
import com.devmate.knowledge.dto.ProjectGuide;
import java.util.*;
import static com.devmate.knowledge.dto.ProjectGuide.*;

/** Rejects unsupported claims/links rather than silently laundering invalid model references. */
public final class ProjectGuideValidator {
    private ProjectGuideValidator() { }
    public static ProjectGuide validate(Content content, ProjectGuideEvidenceService.Sample sample) {
        if (content == null || content.project() == null) fail("content", "缺少必填内容");
        Set<String> allowed = new HashSet<>(); sample.evidence().forEach(item -> allowed.add(item.id()));
        var p = content.project();
        text(p.name(), 100, "project.name"); text(p.type(), 100, "project.type"); text(p.purpose(), 300, "project.purpose"); text(p.audience(), 200, "project.audience");
        text(p.input(), 250, "project.input"); text(p.output(), 250, "project.output"); text(p.scenario(), 600, "project.scenario");
        fact(p.conclusionStatus(), p.evidenceIds(), allowed, "project"); state(p.implementationStatus(), "project");
        list(content.modules(), 20, "modules"); list(content.flows(), 3, "flows"); list(content.glossary(), 20, "glossary"); strings(content.openQuestions(), 12, 400, "openQuestions");
        if (content.modules().isEmpty()) fail("modules", "至少需要一个模块");
        Set<String> moduleIds = new HashSet<>();
        for (var m : content.modules()) { if (!moduleIds.add(m.id())) fail("modules.id", "模块标识重复"); id(m.id(), "modules.id"); }
        Set<String> flowIds = new HashSet<>();
        for (int i = 0; i < content.modules().size(); i++) {
            var m = content.modules().get(i);
            String path = "modules[" + i + "]";
            text(m.name(), 100, path + ".name"); text(m.responsibility(), 400, path + ".responsibility"); strings(m.capabilities(), 8, 200, path + ".capabilities");
            text(m.input(), 250, path + ".input"); text(m.output(), 250, path + ".output"); refs(m.relatedModuleIds(), moduleIds, path + ".relatedModuleIds");
            fact(m.conclusionStatus(), m.evidenceIds(), allowed, path); state(m.implementationStatus(), path);
        }
        for (int i = 0; i < content.flows().size(); i++) {
            var f = content.flows().get(i);
            String path = "flows[" + i + "]";
            if (!flowIds.add(f.id())) fail(path + ".id", "流程标识重复"); id(f.id(), path + ".id");
            text(f.name(), 100, path + ".name"); text(f.goal(), 350, path + ".goal"); text(f.trigger(), 300, path + ".trigger"); strings(f.actors(), 8, 100, path + ".actors");
            refs(f.moduleIds(), moduleIds, path + ".moduleIds"); list(f.steps(), 10, path + ".steps"); list(f.rules(), 8, path + ".rules"); list(f.outcomes(), 8, path + ".outcomes");
            strings(f.unknowns(), 8, 300, path + ".unknowns");
            if (f.steps().isEmpty()) fail(path + ".steps", "至少需要一个步骤");
            for (int j = 0; j < f.steps().size(); j++) {
                var step = f.steps().get(j);
                String stepPath = path + ".steps[" + j + "]";
                text(step.action(), 300, stepPath + ".action"); text(step.input(), 250, stepPath + ".input"); text(step.result(), 300, stepPath + ".result");
                refs(step.moduleIds(), moduleIds, stepPath + ".moduleIds"); fact(step.conclusionStatus(), step.evidenceIds(), allowed, stepPath);
            }
            claims(f.rules(), allowed, path + ".rules");
            claims(f.outcomes(), allowed, path + ".outcomes");
        }
        for (int i = 0; i < content.glossary().size(); i++) {
            var term = content.glossary().get(i);
            text(term.term(), 100, "glossary[" + i + "].term");
            text(term.explanation(), 250, "glossary[" + i + "].explanation");
        }
        if (content.overview() != null) {
            list(content.overview().highlights(), 4, "overview.highlights");
            for (int i = 0; i < content.overview().highlights().size(); i++) {
                var highlight = content.overview().highlights().get(i);
                String path = "overview.highlights[" + i + "]";
                text(highlight.title(), 100, path + ".title"); refs(highlight.moduleIds(), moduleIds, path + ".moduleIds");
                if (highlight.moduleIds().isEmpty()) fail(path + ".moduleIds", "亮点必须关联模块");
                for (var module : content.modules()) {
                    if (highlight.moduleIds().contains(module.id())
                            && (module.implementationStatus() == ImplementationStatus.PLANNED
                            || module.implementationStatus() == ImplementationStatus.UNCLEAR)) fail(path + ".moduleIds", "亮点不能关联计划或未知模块");
                }
                text(highlight.problem(), 300, path + ".problem"); text(highlight.solution(), 400, path + ".solution"); text(highlight.learningValue(), 300, path + ".learningValue");
                text(highlight.suggestedExercise(), 300, path + ".suggestedExercise"); text(highlight.limitation(), 300, path + ".limitation");
                fact(highlight.conclusionStatus(), highlight.evidenceIds(), allowed, path); state(highlight.implementationStatus(), path);
                // A planned/unknown feature is not an implemented highlight.
                if (highlight.implementationStatus() == ImplementationStatus.PLANNED
                        || highlight.implementationStatus() == ImplementationStatus.UNCLEAR
                        || highlight.conclusionStatus() == ConclusionStatus.UNKNOWN) fail(path, "亮点必须有实现依据");
            }
        }
        var normalized = new Content(content.project(), content.modules(), content.flows(), content.glossary(),
                content.openQuestions(), content.overview(), ProjectGuideAppearance.sanitize(content.appearance(), moduleIds));
        return new ProjectGuide("project-guide-v1", sample.snapshot(), normalized, sample.coverage(), sample.evidence(),
                ProjectGuideLanguages.detect(sample.indexedPaths()));
    }
    private static void claims(List<Claim> claims, Set<String> allowed, String path) {
        for (int i = 0; i < claims.size(); i++) {
            var claim = claims.get(i);
            text(claim.text(), 400, path + "[" + i + "].text");
            fact(claim.conclusionStatus(), claim.evidenceIds(), allowed, path + "[" + i + "]");
        }
    }
    private static void fact(ConclusionStatus status, List<String> ids, Set<String> allowed, String path) {
        if (status == null) fail(path + ".conclusionStatus", "缺少结论状态");
        refs(ids, allowed, path + ".evidenceIds");
        if (status != ConclusionStatus.UNKNOWN && ids.isEmpty()) fail(path + ".evidenceIds", "确定或推断结论需要真实证据");
    }
    private static void state(ImplementationStatus state, String path) {
        if (state == null) fail(path + ".implementationStatus", "缺少实现状态");
    }
    private static void refs(List<String> ids, Set<String> allowed, String path) {
        list(ids, 20, path);
        if (new HashSet<>(ids).size() != ids.size()) fail(path, "引用重复");
        if (!allowed.containsAll(ids)) fail(path, "引用了本轮未提供的证据或模块");
    }
    private static void id(String value, String path) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,60}")) fail(path, "标识格式不正确");
    }
    private static void text(String value, int max, String path) {
        if (value == null || value.isBlank()) fail(path, "必填文字为空");
        if (value.length() > max) fail(path, "文字超过 " + max + " 字符上限");
    }
    private static void strings(List<String> values, int max, int chars, String path) {
        list(values, max, path);
        for (int i = 0; i < values.size(); i++) text(values.get(i), chars, path + "[" + i + "]");
    }
    private static void list(List<?> values, int max, String path) {
        if (values == null) fail(path, "缺少列表，应提供数组");
        if (values.size() > max) fail(path, "列表超过 " + max + " 项上限");
        if (values.stream().anyMatch(Objects::isNull)) fail(path, "列表包含空项");
    }
    private static void fail(String path, String rule) {
        // Paths and rules are host-owned constants/indices; never include source or model values.
        throw new AiReviewException("模型导览校验失败：" + path + "，" + rule);
    }
}
