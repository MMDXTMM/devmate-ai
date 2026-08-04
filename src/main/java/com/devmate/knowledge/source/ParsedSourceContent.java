package com.devmate.knowledge.source;

import java.util.List;

public record ParsedSourceContent(
        String packageName,
        List<ParsedSourceChunk> chunks
) {
    public ParsedSourceContent {
        chunks = List.copyOf(chunks);
    }
}
