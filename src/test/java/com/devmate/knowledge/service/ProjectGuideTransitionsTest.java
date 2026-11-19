package com.devmate.knowledge.service;

import com.devmate.knowledge.dto.ProjectGuide;
import com.devmate.agent.model.AiReviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ProjectGuideTransitionsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ProjectGuideEvidenceService.Sample sample() {
        return new ProjectGuideEvidenceService.Sample(new ProjectGuide.Snapshot("abc", "digest", "runtime-first-v3"),
                new ProjectGuide.Coverage(1, 1, 1, 30, 40, 48000, List.of()),
                List.of(new ProjectGuide.Evidence("42", "SOURCE_CODE", "abc", "run", "main.py", 1, 4, "if missing: return error", false)), List.of("main.py"));
    }
    private ObjectNode content() throws Exception {
        return (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/guide/cli-guide.json"));
    }
    @Test void preservesTerminalBranchesAndMapsTheirEvidence() throws Exception {
        var json = content();
        ((ObjectNode)json.withArray("flows").get(0)).set("transitions", mapper.valueToTree(List.of(
                new ProjectGuide.Transition(1, null, "输入不存在", "返回错误并停止", ProjectGuide.ConclusionStatus.OBSERVED, List.of("42")))));
        var c = mapper.treeToValue(json, ProjectGuide.Content.class);
        var validated = ProjectGuideValidator.validate(c, sample());
        assertThat(validated.content().flows().getFirst().transitions()).singleElement().satisfies(t -> {
            assertThat(t.toStep()).isNull(); assertThat(t.evidenceIds()).containsExactly("42");
        });
        var refs = new ProjectGuideEvidenceReferences(sample());
        var aliased = mapper.readValue(mapper.writeValueAsString(c).replace("\"42\"", "\"E1\""), ProjectGuide.Content.class);
        assertThat(refs.restore(aliased).flows().getFirst().transitions().getFirst().evidenceIds()).containsExactly("42");
    }
    @Test void rejectsInvalidStepTargetsAndUnsupportedBranchEvidence() throws Exception {
        for (var transition : List.of(
                new ProjectGuide.Transition(1, 99, "条件", "结果", ProjectGuide.ConclusionStatus.OBSERVED, List.of("42")),
                new ProjectGuide.Transition(1, null, "条件", "结果", ProjectGuide.ConclusionStatus.OBSERVED, List.of("99")),
                new ProjectGuide.Transition(1, null, "条件", "结果", ProjectGuide.ConclusionStatus.UNKNOWN, List.of()))) {
            var json = content();
            ((ObjectNode)json.withArray("flows").get(0)).set("transitions", mapper.valueToTree(List.of(transition)));
            var c = mapper.treeToValue(json, ProjectGuide.Content.class);
            assertThatThrownBy(() -> ProjectGuideValidator.validate(c, sample())).isInstanceOf(AiReviewException.class).hasMessageContaining("transitions");
        }
    }
    @Test void doesNotTreatMissingEvidenceAsPartialImplementation() throws Exception {
        var json = content(); ((ObjectNode)json.get("project")).put("conclusionStatus", "UNKNOWN");
        var c = mapper.treeToValue(json, ProjectGuide.Content.class);
        assertThatThrownBy(() -> ProjectGuideValidator.validate(c, sample())).hasMessageContaining("未核实不能判");
        ((ObjectNode)json.get("project")).put("implementationStatus", "UNCLEAR");
        assertThat(ProjectGuideValidator.validate(mapper.treeToValue(json, ProjectGuide.Content.class), sample()).content().project().implementationStatus())
                .isEqualTo(ProjectGuide.ImplementationStatus.UNCLEAR);
    }
}
