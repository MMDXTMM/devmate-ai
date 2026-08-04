package com.devmate.knowledge.retrieval;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;

import java.util.Set;

record RetrievalCandidate(
        KnowledgeChunk chunk,
        KnowledgeDocument document,
        double score,
        int estimatedTokens,
        Set<String> reasons
) {
}
