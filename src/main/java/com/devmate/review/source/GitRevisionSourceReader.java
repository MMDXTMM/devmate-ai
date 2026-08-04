package com.devmate.review.source;

import com.devmate.knowledge.config.SourceImportProperties;
import com.devmate.knowledge.source.SourceImportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class GitRevisionSourceReader {

    private final SourceImportProperties properties;

    public GitRevisionSourceReader(SourceImportProperties properties) {
        this.properties = properties;
    }

    public Optional<String> read(Path repositoryRoot, String revision, String filePath) {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryRoot.resolve(".git").toFile())
                .build()) {
            ObjectId treeId = repository.resolve(revision + "^{tree}");
            if (treeId == null) {
                throw new SourceImportException("Git版本不存在或历史对象不完整");
            }
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, treeId)) {
                if (treeWalk == null) {
                    return Optional.empty();
                }
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                if (loader.getSize() > properties.getMaxFileSizeBytes()) {
                    throw new SourceImportException("基准版本Java文件超过单文件大小限制：" + filePath);
                }
                return Optional.of(new String(loader.getBytes(), StandardCharsets.UTF_8));
            }
        } catch (SourceImportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SourceImportException("读取Git版本源码失败：" + filePath, exception);
        }
    }
}
