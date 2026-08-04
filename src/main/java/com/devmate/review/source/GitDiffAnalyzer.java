package com.devmate.review.source;

import com.devmate.knowledge.source.SourceImportException;
import com.devmate.review.model.GitChangedFile;
import com.devmate.review.model.GitDiffResult;
import com.devmate.review.model.LineRange;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitDiffAnalyzer {

    public GitDiffResult analyze(Path repositoryRoot, String requestedBase, String requestedTarget) {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryRoot.resolve(".git").toFile())
                .build();
             RevWalk revWalk = new RevWalk(repository)) {
            RevCommit target = resolveCommit(
                    repository,
                    revWalk,
                    StringUtils.hasText(requestedTarget) ? requestedTarget : Constants.HEAD,
                    "目标"
            );
            RevCommit base = StringUtils.hasText(requestedBase)
                    ? resolveCommit(repository, revWalk, requestedBase, "基准")
                    : resolveParent(revWalk, target);
            List<GitChangedFile> files = scan(repository, base, target);
            return new GitDiffResult(base.name(), target.name(), files);
        } catch (SourceImportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SourceImportException("读取Git差异失败", exception);
        }
    }

    private RevCommit resolveCommit(
            Repository repository,
            RevWalk revWalk,
            String revision,
            String label
    ) throws IOException {
        ObjectId objectId = repository.resolve(revision);
        if (objectId == null) {
            throw new SourceImportException(label + "版本不存在或不在当前导入历史中");
        }
        try {
            return revWalk.parseCommit(objectId);
        } catch (IOException exception) {
            throw new SourceImportException(label + "版本对象不完整，请重新导入源码", exception);
        }
    }

    private RevCommit resolveParent(RevWalk revWalk, RevCommit target) {
        if (target.getParentCount() == 0) {
            throw new SourceImportException("目标版本没有父提交，无法自动确定基准版本");
        }
        try {
            return revWalk.parseCommit(target.getParent(0).getId());
        } catch (IOException exception) {
            throw new SourceImportException("Git历史深度不足，请重新导入源码", exception);
        }
    }

    private List<GitChangedFile> scan(
            Repository repository,
            RevCommit base,
            RevCommit target
    ) throws IOException {
        try (ObjectReader reader = repository.newObjectReader();
             DiffFormatter formatter = new DiffFormatter(OutputStream.nullOutputStream())) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, base.getTree());
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, target.getTree());
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);

            List<GitChangedFile> result = new ArrayList<>();
            for (DiffEntry entry : formatter.scan(oldTree, newTree)) {
                List<Edit> edits = formatter.toFileHeader(entry).toEditList();
                int additions = edits.stream().mapToInt(edit -> edit.getEndB() - edit.getBeginB()).sum();
                int deletions = edits.stream().mapToInt(edit -> edit.getEndA() - edit.getBeginA()).sum();
                List<LineRange> ranges = edits.stream()
                        .filter(edit -> edit.getEndB() > edit.getBeginB())
                        .map(edit -> new LineRange(edit.getBeginB() + 1, edit.getEndB()))
                        .toList();
                result.add(new GitChangedFile(
                        normalizePath(entry.getOldPath()),
                        normalizePath(entry.getNewPath()),
                        entry.getChangeType().name(),
                        additions,
                        deletions,
                        ranges
                ));
            }
            return result;
        }
    }

    private String normalizePath(String path) {
        return DiffEntry.DEV_NULL.equals(path) ? null : path;
    }
}
