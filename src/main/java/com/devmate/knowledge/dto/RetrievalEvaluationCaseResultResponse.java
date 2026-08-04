package com.devmate.knowledge.dto;

public record RetrievalEvaluationCaseResultResponse(
        String name,
        String query,
        int topK,
        boolean expectationResolved,
        int expectedCount,
        int relevantRetrieved,
        double recallAtK,
        double precisionAtK,
        double reciprocalRank,
        String note
) {
}
