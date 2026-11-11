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
}
