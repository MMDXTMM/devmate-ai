package com.devmate.knowledge.source;

import java.util.List;

public record ParsedSourceFile(
        ScannedSourceFile sourceFile,
        String packageName,
        List<ParsedSourceChunk> chunks,
        List<ParsedCodeReference> references
) {
    public ParsedSourceFile {
        chunks = List.copyOf(chunks);
        references = List.copyOf(references);
    }
}
