package com.devmate.knowledge.source;

public record ParsedCodeReference(
        String sourceSymbolName,
        String referenceKind,
        String referenceName,
        String qualifier,
        Integer argumentCount,
        int startLine,
        int endLine,
        String metadataJson
) {
}
