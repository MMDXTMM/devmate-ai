package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.RetrievalEvaluationCase;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record RetrievalEvaluationCaseResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String datasetVersion,
        String name,
        String query,
        String expectedFilePath,
        String expectedSymbolName,
        int topK,
        boolean enabled
) {
    public static RetrievalEvaluationCaseResponse from(RetrievalEvaluationCase value) {
        return new RetrievalEvaluationCaseResponse(
                value.getId(), value.getDatasetVersion(), value.getName(), value.getQueryText(),
                value.getExpectedFilePath(), value.getExpectedSymbolName(), value.getTopK(),
                Integer.valueOf(1).equals(value.getEnabled())
        );
    }
}
