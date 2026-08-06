package com.devmate.knowledge.service;

import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.knowledge.source.ScannedSourceFile;

import java.util.List;

record SourceImportPlan(
        List<ScannedSourceFile> filesToParse,
        List<ParsedSourceFile> reusedFiles
) {
    SourceImportPlan {
        filesToParse = List.copyOf(filesToParse);
        reusedFiles = List.copyOf(reusedFiles);
    }
}
