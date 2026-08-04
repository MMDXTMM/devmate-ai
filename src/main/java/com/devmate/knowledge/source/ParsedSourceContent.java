package com.devmate.knowledge.source;

import java.util.List;

public record ParsedSourceContent(
        String packageName,
        List<ParsedSourceChunk> chunks,
        List<ParsedCodeReference> references
) {
    public ParsedSourceContent {
        chunks = List.copyOf(chunks);
        references = List.copyOf(references);
    }
}
