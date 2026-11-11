package com.devmate.knowledge.service;

import com.devmate.agent.model.AiReviewException;
import com.devmate.knowledge.dto.ProjectGuide;
import java.util.*;
import static com.devmate.knowledge.dto.ProjectGuide.*;

/** Rejects unsupported claims/links rather than silently laundering invalid model references. */
public final class ProjectGuideValidator {
    private ProjectGuideValidator() { }
    public static ProjectGuide validate(Content content, ProjectGuideEvidenceService.Sample sample) {
        if (content == null || content.project() == null) fail();
        Set<String> allowed = new HashSet<>(); sample.evidence().forEach(item -> allowed.add(item.id()));
        var p = content.project();
        text(p.name(), 100); text(p.type(), 100); text(p.purpose(), 300); text(p.audience(), 200);
        text(p.input(), 250); text(p.output(), 250); text(p.scenario(), 600);
        fact(p.conclusionStatus(), p.evidenceIds(), allowed); state(p.implementationStatus());
        list(content.modules(), 20); list(content.flows(), 3); list(content.glossary(), 20); strings(content.openQuestions(), 12, 400);
        if (content.modules().isEmpty()) fail();
        Set<String> moduleIds = new HashSet<>();
        for (var m : content.modules()) { if (m == null || !moduleIds.add(m.id())) fail(); id(m.id()); }
        Set<String> flowIds = new HashSet<>();
        for (var m : content.modules()) {
            text(m.name(), 100); text(m.responsibility(), 400); strings(m.capabilities(), 8, 200);
            text(m.input(), 250); text(m.output(), 250); refs(m.relatedModuleIds(), moduleIds);
            fact(m.conclusionStatus(), m.evidenceIds(), allowed); state(m.implementationStatus());
        }
        for (var f : content.flows()) {
            if (f == null || !flowIds.add(f.id())) fail(); id(f.id());
            text(f.name(), 100); text(f.goal(), 350); text(f.trigger(), 300); strings(f.actors(), 8, 100);
            refs(f.moduleIds(), moduleIds); list(f.steps(), 10); list(f.rules(), 8); list(f.outcomes(), 8);
            strings(f.unknowns(), 8, 300);
            if (f.steps().isEmpty()) fail();
            for (var step : f.steps()) {
                if (step == null) fail(); text(step.action(), 300); text(step.input(), 250); text(step.result(), 300);
                refs(step.moduleIds(), moduleIds); fact(step.conclusionStatus(), step.evidenceIds(), allowed);
            }
            for (var claim : java.util.stream.Stream.concat(f.rules().stream(), f.outcomes().stream()).toList()) {
                if (claim == null) fail(); text(claim.text(), 400); fact(claim.conclusionStatus(), claim.evidenceIds(), allowed);
            }
        }
        for (var term : content.glossary()) { if (term == null) fail(); text(term.term(), 100); text(term.explanation(), 250); }
        return new ProjectGuide("project-guide-v1", sample.snapshot(), content, sample.coverage(), sample.evidence());
    }
    private static void fact(ConclusionStatus status, List<String> ids, Set<String> allowed) {
        if (status == null) fail(); refs(ids, allowed);
        if (status != ConclusionStatus.UNKNOWN && ids.isEmpty()) fail();
    }
    private static void state(ImplementationStatus state) { if (state == null) fail(); }
    private static void refs(List<String> ids, Set<String> allowed) {
        list(ids, 20); if (new HashSet<>(ids).size() != ids.size() || !allowed.containsAll(ids)) fail();
    }
    private static void id(String value) { if (value == null || !value.matches("[a-zA-Z0-9_-]{1,60}")) fail(); }
    private static void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max) fail(); }
    private static void strings(List<String> values, int max, int chars) { list(values, max); values.forEach(value -> text(value, chars)); }
    private static void list(List<?> values, int max) { if (values == null || values.size() > max || values.stream().anyMatch(Objects::isNull)) fail(); }
    private static void fail() { throw new AiReviewException("模型导览结构、状态或证据引用不符合契约，请重试"); }
}
