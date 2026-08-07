package com.devmate.knowledge.service;

public interface SourceStructureUsageChecker {

    default void assertImportAllowed(Long projectId) {
    }

    void assertRebuildAllowed(Long projectId, String revision);
}
