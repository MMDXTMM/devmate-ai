package com.devmate.knowledge.service;

import com.devmate.agent.model.AiReviewException;
import com.devmate.knowledge.dto.ProjectGuide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.devmate.knowledge.dto.ProjectGuide.*;

/** Short request-local model references; storage and API retain the original string chunk IDs. */
public final class ProjectGuideEvidenceReferences {
    private final ProjectGuideEvidenceService.Sample modelSample;
    private final Map<String, String> originalIds;

    public ProjectGuideEvidenceReferences(ProjectGuideEvidenceService.Sample sample) {
        Map<String, String> ids = new LinkedHashMap<>();
        var evidence = sample.evidence().stream().map(item -> {
            String alias = "E" + (ids.size() + 1);
            ids.put(alias, item.id());
            return new Evidence(alias, item.sourceKind(), item.revision(), item.symbolName(), item.filePath(),
                    item.startLine(), item.endLine(), item.code(), item.truncated());
        }).toList();
        originalIds = Map.copyOf(ids);
        modelSample = new ProjectGuideEvidenceService.Sample(sample.snapshot(), sample.coverage(), evidence, sample.indexedPaths());
    }

    public ProjectGuideEvidenceService.Sample modelSample() { return modelSample; }

    public Content restore(Content content) {
        if (content == null) return null;
        var p = content.project();
        var project = p == null ? null : new ProjectInfo(p.name(), p.type(), p.purpose(), p.audience(), p.input(),
                p.output(), p.scenario(), p.conclusionStatus(), p.implementationStatus(), ids(p.evidenceIds(), "project.evidenceIds"));
        var modules = map(content.modules(), m -> new ProjectGuide.Module(m.id(), m.name(), m.responsibility(), m.capabilities(),
                m.input(), m.output(), m.relatedModuleIds(), m.conclusionStatus(), m.implementationStatus(),
                ids(m.evidenceIds(), "modules.evidenceIds")));
        var flows = map(content.flows(), f -> new Flow(f.id(), f.name(), f.goal(), f.actors(), f.trigger(), f.moduleIds(),
                map(f.steps(), step -> new Step(step.action(), step.input(), step.result(), step.moduleIds(),
                        step.conclusionStatus(), ids(step.evidenceIds(), "flows.steps.evidenceIds"))),
                map(f.rules(), c -> claim(c, "flows.rules.evidenceIds")),
                map(f.outcomes(), c -> claim(c, "flows.outcomes.evidenceIds")), f.unknowns()));
        var overview = content.overview() == null ? null : new Overview(map(content.overview().highlights(), h ->
                new Highlight(h.title(), h.moduleIds(), h.problem(), h.solution(), h.learningValue(), h.suggestedExercise(),
                        h.limitation(), h.conclusionStatus(), h.implementationStatus(), ids(h.evidenceIds(), "overview.highlights.evidenceIds"))));
        return new Content(project, modules, flows, content.glossary(), content.openQuestions(), overview, content.appearance());
    }

    private Claim claim(Claim claim, String path) {
        return new Claim(claim.text(), claim.conclusionStatus(), ids(claim.evidenceIds(), path));
    }

    private List<String> ids(List<String> ids, String path) {
        if (ids == null) return null;
        return ids.stream().map(id -> {
            if (id == null || !originalIds.containsKey(id)) {
                throw new AiReviewException("模型导览校验失败：" + path + "，引用了本轮未提供的证据编号");
            }
            return originalIds.get(id);
        }).toList();
    }

    private static <T, R> List<R> map(List<T> values, Function<T, R> mapper) {
        return values == null ? null : values.stream().map(value -> value == null ? null : mapper.apply(value)).toList();
    }
}
