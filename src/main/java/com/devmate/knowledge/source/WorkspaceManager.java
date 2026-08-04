package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WorkspaceManager {

    private final SourceImportProperties properties;

    public WorkspaceManager(SourceImportProperties properties) {
        this.properties = properties;
    }

    public Path createTaskDirectory(Long projectId, Long taskId) {
        Path target = resolveTaskDirectory(projectId, taskId);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                throw new SourceImportException("源码任务目录已存在");
            }
            return target;
        } catch (IOException exception) {
            throw new SourceImportException("无法创建源码工作目录", exception);
        }
    }

    public Path requireTaskDirectory(Long projectId, Long taskId) {
        Path target = resolveTaskDirectory(projectId, taskId);
        if (!Files.isDirectory(target.resolve(".git"))) {
            throw new SourceImportException("源码工作目录不存在，请重新导入项目源码");
        }
        return target;
    }

    private Path resolveTaskDirectory(Long projectId, Long taskId) {
        Path root = properties.getWorkspaceRoot().toAbsolutePath().normalize();
        Path target = root.resolve(projectId.toString()).resolve(taskId.toString()).normalize();
        if (!target.startsWith(root)) {
            throw new SourceImportException("源码工作目录不安全");
        }
        return target;
    }
}
