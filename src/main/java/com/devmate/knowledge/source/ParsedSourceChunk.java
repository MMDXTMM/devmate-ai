package com.devmate.knowledge.source;

import java.util.List;
import java.util.Map;

public record ParsedSourceChunk(
        int chunkIndex,
        String chunkType,
        String symbolName,
        List<String> annotations,
        Integer parameterCount,
        Map<String, Object> metadata,
        String content,
        String contentHash,
        int startLine,
        int endLine
) {
    public ParsedSourceChunk {
        annotations = List.copyOf(annotations);
        metadata = Map.copyOf(metadata);
    }
}
