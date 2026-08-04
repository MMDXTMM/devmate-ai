package com.devmate.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "devmate.source")
public class SourceImportProperties {

    private Path workspaceRoot = Path.of(System.getProperty("user.dir"), "workspace");
    private int cloneTimeoutSeconds = 60;
    private int cloneDepth = 50;
    private int maxJavaFiles = 1000;
    private long maxFileSizeBytes = 1024 * 1024;
    private long maxTotalSizeBytes = 50L * 1024 * 1024;
    private String gitUsername = "x-access-token";
    private String gitToken;

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public int getCloneTimeoutSeconds() {
        return cloneTimeoutSeconds;
    }

    public int getCloneDepth() {
        return cloneDepth;
    }

    public void setCloneDepth(int cloneDepth) {
        this.cloneDepth = cloneDepth;
    }

    public void setCloneTimeoutSeconds(int cloneTimeoutSeconds) {
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    public int getMaxJavaFiles() {
        return maxJavaFiles;
    }

    public void setMaxJavaFiles(int maxJavaFiles) {
        this.maxJavaFiles = maxJavaFiles;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long getMaxTotalSizeBytes() {
        return maxTotalSizeBytes;
    }

    public void setMaxTotalSizeBytes(long maxTotalSizeBytes) {
        this.maxTotalSizeBytes = maxTotalSizeBytes;
    }

    public String getGitUsername() {
        return gitUsername;
    }

    public void setGitUsername(String gitUsername) {
        this.gitUsername = gitUsername;
    }

    public String getGitToken() {
        return gitToken;
    }

    public void setGitToken(String gitToken) {
        this.gitToken = gitToken;
    }
}
