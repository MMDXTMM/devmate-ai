package com.devmate.knowledge.source;

import java.nio.file.Path;

public record ScannedSourceFile(
        String fileName,
        String relativePath,
        String pathHash,
        String contentHash,
        long size,
        Path sourcePath
) {
}
