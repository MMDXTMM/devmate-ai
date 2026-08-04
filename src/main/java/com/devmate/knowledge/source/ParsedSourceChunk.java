package com.devmate.knowledge.source;

import java.util.List;

public record ParsedSourceChunk(
        int chunkIndex,
        String chunkType,
        String symbolName,
        List<String> annotations,
        Integer parameterCount,
        String content,
        String contentHash,
        int startLine,
        int endLine
) {
    public ParsedSourceChunk {
        annotations = List.copyOf(annotations);
    }
}
