package com.devmate.agent.model;

import com.devmate.knowledge.dto.ProjectGuide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.converter.BeanOutputConverter;

/** Spring AI's default Java schema omits null even for boxed Integer fields. */
final class ProjectGuideOutputConverter extends BeanOutputConverter<ProjectGuide.Content> {
    ProjectGuideOutputConverter() {
        super(ProjectGuide.Content.class);
    }

    @Override
    protected void postProcessSchema(JsonNode schema) {
        for (JsonNode target : schema.findValues("toStep")) {
            if (target instanceof ObjectNode field) {
                field.putArray("type").add("integer").add("null");
                field.put("description", "1-based next step; null means this branch ends with its result.");
            }
        }
    }
}
