package com.devmate.knowledge.service;

import com.devmate.agent.model.AiReviewException;
import com.devmate.knowledge.dto.ProjectGuide;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectGuideEvidenceReferencesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ORIGINAL_ID = "2088547010527170561";

    private ProjectGuideEvidenceService.Sample sample(String id) {
        return new ProjectGuideEvidenceService.Sample(new ProjectGuide.Snapshot("rev", "digest", "policy"),
                new ProjectGuide.Coverage(1, 1, 1, 20, 40, 48000, List.of("未运行")),
                List.of(new ProjectGuide.Evidence(id, "SOURCE_CODE", "rev", "main", "main.py", 1, 5, "real source", false)),
                List.of("main.py"));
    }

    private ProjectGuide.Content content(String id) throws Exception {
        String json = new String(getClass().getResourceAsStream("/guide/cli-guide.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\"42\"", "\"" + id + "\"");
        return mapper.readValue(json, ProjectGuide.Content.class);
    }

    @Test
    void restoresExactBigintStringsAndAllNestedEvidenceWithoutChangingBusinessTextOrLocations() throws Exception {
        var original = sample(ORIGINAL_ID);
        var refs = new ProjectGuideEvidenceReferences(original);
        assertThat(refs.modelSample().evidence().getFirst().id()).isEqualTo("E1");
        assertThat(original.evidence().getFirst().id()).isEqualTo(ORIGINAL_ID);
        var c = content("E1");
        var highlight = new ProjectGuide.Highlight("E1 是一段说明，不应替换", List.of("convert"), "输入问题", "校验输入", "学习校验",
                "设计测试", "未运行", ProjectGuide.ConclusionStatus.OBSERVED, ProjectGuide.ImplementationStatus.PARTIAL, List.of("E1"));
        c = new ProjectGuide.Content(c.project(), c.modules(), c.flows(), c.glossary(), c.openQuestions(),
                new ProjectGuide.Overview(List.of(highlight)));
        var restored = refs.restore(c);
        assertThat(mapper.writeValueAsString(restored)).isEqualTo(mapper.writeValueAsString(c)
                .replace("\"evidenceIds\":[\"E1\"]", "\"evidenceIds\":[\"" + ORIGINAL_ID + "\"]"));
        var validated = ProjectGuideValidator.validate(restored, original);
        assertThat(validated.evidence().getFirst().id()).isEqualTo(ORIGINAL_ID);
        assertThat(validated.evidence().getFirst().startLine()).isEqualTo(1);
        assertThat(validated.content().overview().highlights().getFirst().title()).contains("E1");
    }

    @Test
    void rejectsUnknownAliasesAndDuplicateReferencesWithoutDroppingThem() throws Exception {
        var refs = new ProjectGuideEvidenceReferences(sample(ORIGINAL_ID));
        for (String invalid : List.of("E99", ORIGINAL_ID, "private-value")) {
            assertThatThrownBy(() -> refs.restore(content(invalid))).isInstanceOf(AiReviewException.class)
                    .hasMessageContaining("project.evidenceIds", "本轮未提供").hasMessageNotContaining(invalid);
        }
        String duplicate = mapper.writeValueAsString(content("E1")).replace("\"evidenceIds\":[\"E1\"]", "\"evidenceIds\":[\"E1\",\"E1\"]");
        var restored = refs.restore(mapper.readValue(duplicate, ProjectGuide.Content.class));
        assertThatThrownBy(() -> ProjectGuideValidator.validate(restored, sample(ORIGINAL_ID)))
                .isInstanceOf(AiReviewException.class).hasMessageContaining("引用重复");
    }

    @Test
    void keepsAliasMappingsLocalToEachFixedSnapshot() throws Exception {
        var first = new ProjectGuideEvidenceReferences(sample(ORIGINAL_ID));
        var second = new ProjectGuideEvidenceReferences(sample("2088547010527170562"));
        assertThat(first.restore(content("E1")).project().evidenceIds()).containsExactly(ORIGINAL_ID);
        assertThat(second.restore(content("E1")).project().evidenceIds()).containsExactly("2088547010527170562");
    }
}
