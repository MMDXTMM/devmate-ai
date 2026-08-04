package com.devmate.knowledge.source;

import java.nio.file.Path;

public interface GitSourceClient {

    GitCloneResult cloneRepository(String repositoryUrl, String branch, Path targetDirectory);
}
