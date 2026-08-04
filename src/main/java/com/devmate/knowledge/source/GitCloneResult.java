package com.devmate.knowledge.source;

import java.nio.file.Path;

public record GitCloneResult(Path repositoryRoot, String revision) {
}
