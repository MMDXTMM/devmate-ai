package com.devmate.knowledge.service;

import com.devmate.agent.model.AiReviewException;
import com.devmate.knowledge.dto.ProjectGuide;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ProjectGuideValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private String json() throws Exception {
        return new String(getClass().getResourceAsStream("/guide/cli-guide.json").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    private ProjectGuideEvidenceService.Sample sample() {
        return new ProjectGuideEvidenceService.Sample(new ProjectGuide.Snapshot("revision", "digest", "policy"),
                new ProjectGuide.Coverage(3, 1, 1, 70, 40, 48000, List.of("未运行")),
                List.of(new ProjectGuide.Evidence("42", "SOURCE_CODE", "revision", "main.py:1", "main.py", 1, 4, "real code", false)), List.of("main.py"));
    }
    private ProjectGuide.Content withHighlights(String moduleId, String evidenceId, ProjectGuide.ImplementationStatus state) throws Exception {
        var c = mapper.readValue(json(), ProjectGuide.Content.class);
        var highlight = new ProjectGuide.Highlight("输入校验", List.of(moduleId), "缺少输入无法处理", "检查文件后再汇总",
                "学习先校验再处理的边界", "尝试设计缺少输入的测试", "未运行目标代码",
                ProjectGuide.ConclusionStatus.OBSERVED, state, List.of(evidenceId));
        return new ProjectGuide.Content(c.project(), c.modules(), c.flows(), c.glossary(), c.openQuestions(),
                new ProjectGuide.Overview(List.of(highlight)));
    }
    @Test void reportsSafeFieldLocationWithoutEchoingRejectedValues() throws Exception {
        var tree = mapper.readTree(json());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("modules").get(0)).put("input", "");
        assertThatThrownBy(() -> ProjectGuideValidator.validate(mapper.treeToValue(tree, ProjectGuide.Content.class), sample()))
                .isInstanceOf(AiReviewException.class).hasMessageContaining("modules[0].input", "必填文字为空");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("modules").get(0)).put("input", "正常输入");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("project")).putArray("evidenceIds").add("secret-private-value");
        assertThatThrownBy(() -> ProjectGuideValidator.validate(mapper.treeToValue(tree, ProjectGuide.Content.class), sample()))
                .isInstanceOf(AiReviewException.class).hasMessageContaining("project.evidenceIds", "本轮未提供")
                .hasMessageNotContaining("secret-private-value");
    }
    @Test void validatesLearningHighlightsAndUsesOnlyHostObservedLanguages() throws Exception {
        var content = withHighlights("convert", "42", ProjectGuide.ImplementationStatus.PARTIAL);
        var guide = ProjectGuideValidator.validate(content, sample());
        assertThat(guide.content().overview().highlights()).hasSize(1);
        assertThat(guide.languages()).containsExactly("Python");
        assertThat(ProjectGuideLanguages.detect(List.of("src/App.java", "ui/main.ts", "test/example.py", "go.mod", "package.json")))
                .containsExactly("Java", "Python", "TypeScript");
        assertThat(ProjectGuideLanguages.detect(List.of("README.md", "design.txt", "package.json"))).isEmpty();
    }
    @Test void refusesInventedModulesForeignEvidenceAndPlannedHighlights() throws Exception {
        for (var content : List.of(withHighlights("missing", "42", ProjectGuide.ImplementationStatus.PARTIAL),
                withHighlights("convert", "foreign", ProjectGuide.ImplementationStatus.PARTIAL),
                withHighlights("convert", "42", ProjectGuide.ImplementationStatus.PLANNED))) {
            assertThatThrownBy(() -> ProjectGuideValidator.validate(content, sample())).isInstanceOf(AiReviewException.class);
        }
    }

    @Test void acceptsCliAndUsesOnlyHostOwnedSnapshotAndPositions() throws Exception {
        var guide = ProjectGuideValidator.validate(mapper.readValue(json(), ProjectGuide.Content.class), sample());
        assertThat(guide.content().project().type()).isEqualTo("Python CLI");
        assertThat(guide.snapshot().contentDigest()).isEqualTo("digest");
        assertThat(guide.evidence()).singleElement().satisfies(e -> assertThat(e.startLine()).isEqualTo(1));
    }
    @Test void rejectsForeignEvidenceAndUnknownModuleInsteadOfDroppingReferences() throws Exception {
        for (String value : List.of(json().replace("\"42\"", "\"foreign\""),
                json().replace("\"moduleIds\":[\"convert\"]", "\"moduleIds\":[\"missing\"]"),
                json().replace("\"evidenceIds\":[\"42\"]", "\"evidenceIds\":[]"),
                json().replace("\"evidenceIds\":[\"42\"]", "\"evidenceIds\":[\"42\",\"42\"]"))) {
            assertThatThrownBy(() -> ProjectGuideValidator.validate(mapper.readValue(value, ProjectGuide.Content.class), sample()))
                    .isInstanceOf(AiReviewException.class).hasMessageNotContaining("foreign");
        }
    }
    @Test void acceptsDocumentPlansWithoutInventingAFlowAndKeepsUnknownSeparate() throws Exception {
        var content = mapper.readValue(json().replace("\"IMPLEMENTED\"", "\"PLANNED\"").replace("Python CLI", "文档设计"), ProjectGuide.Content.class);
        var docs = new ProjectGuide.Content(content.project(), content.modules(), List.of(), List.of(), List.of("仅文档，无运行实现"));
        var result = ProjectGuideValidator.validate(docs, sample());
        assertThat(result.content().flows()).isEmpty();
        assertThat(result.content().project().implementationStatus()).isEqualTo(ProjectGuide.ImplementationStatus.PLANNED);
    }
    @Test void rejectsMissingRequiredCollectionsAndDuplicateModuleIds() throws Exception {
        var content = mapper.readValue(json(), ProjectGuide.Content.class);
        assertThatThrownBy(() -> ProjectGuideValidator.validate(new ProjectGuide.Content(content.project(),
                List.of(content.modules().getFirst(), content.modules().getFirst()), content.flows(), List.of(), List.of()), sample()))
                .isInstanceOf(AiReviewException.class);
        assertThatThrownBy(() -> ProjectGuideValidator.validate(new ProjectGuide.Content(content.project(),
                null, content.flows(), List.of(), List.of()), sample())).isInstanceOf(AiReviewException.class);
    }
    @Test void acceptsNewDomainIconsButDropsUnsafeOrForeignPresentationWithoutLosingBusinessContent() throws Exception {
        var c = mapper.readValue(json(), ProjectGuide.Content.class);
        var input = new ProjectGuide.Content(c.project(), c.modules(), c.flows(), c.glossary(), c.openQuestions(), c.overview(),
                new ProjectGuide.Appearance("🔭", List.of(new ProjectGuide.ModuleIcon("convert", "🧑‍💻"), new ProjectGuide.ModuleIcon("foreign", "🦋"))));
        var validated = ProjectGuideValidator.validate(input, sample());
        assertThat(validated.content().appearance().projectIcon()).isEqualTo("🔭");
        assertThat(validated.content().appearance().modules()).containsExactly(new ProjectGuide.ModuleIcon("convert", "🧑‍💻"));
        var restored = mapper.readValue(mapper.writeValueAsString(validated), ProjectGuide.class);
        assertThat(restored.content().appearance()).isEqualTo(validated.content().appearance());
        for (String invalid : List.of("🌱🌼", "https://example.com/icon.svg", "<img src=x>", "icon", "🔭 explanation", " 🔭")) {
            input = new ProjectGuide.Content(c.project(), c.modules(), c.flows(), c.glossary(), c.openQuestions(), c.overview(),
                    new ProjectGuide.Appearance(invalid, List.of(new ProjectGuide.ModuleIcon("convert", invalid))));
            var result = ProjectGuideValidator.validate(input, sample());
            assertThat(result.content().appearance().projectIcon()).isNull();
            assertThat(result.content().appearance().modules()).isEmpty();
            assertThat(result.content().project()).isEqualTo(c.project());
        }
        assertThat(ProjectGuideValidator.validate(c, sample()).content().appearance()).isNull();
        assertThat(ProjectGuideAppearance.safeIcon("🇨🇳")).isEqualTo("🇨🇳");
        var legacyAppearance = mapper.readValue("{\"projectIcon\":\"🔭\",\"moduleIcons\":{\"convert\":\"🧮\"}}", ProjectGuide.Appearance.class);
        assertThat(legacyAppearance.modules()).containsExactly(new ProjectGuide.ModuleIcon("convert", "🧮"));
    }

}
