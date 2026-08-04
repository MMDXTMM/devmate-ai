package com.devmate.knowledge.source;

import java.nio.file.Path;

public record ScannedSourceFile(
        String fileName,
        String relativePath,
        SourceFileType fileType,
        String pathHash,
        String contentHash,
        long size,
        Path sourcePath
) {
}
