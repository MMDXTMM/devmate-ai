package com.devmate.knowledge.service;

public record SourceImportContext(
        Long projectId,
        Long taskId,
        String repositoryUrl,
        String branch,
        String previousRevision
) {
}
