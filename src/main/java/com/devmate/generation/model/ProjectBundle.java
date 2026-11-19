package com.devmate.generation.model;

import java.util.List;

/** Untrusted model output. It must pass the artifact policy before any file is written. */
public record ProjectBundle(String projectName, String summary, String runInstructions,
                            List<ProjectFile> files, List<String> warnings) {
    public record ProjectFile(String path, String content) { }
}
