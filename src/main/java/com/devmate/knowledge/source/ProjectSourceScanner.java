package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProjectSourceScanner {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", "target", "build", "out", "node_modules"
    );

    private final SourceImportProperties properties;

    public ProjectSourceScanner(SourceImportProperties properties) {
        this.properties = properties;
    }

    public List<ScannedSourceFile> scan(Path repositoryRoot) {
        try {
            Path realRoot = repositoryRoot.toRealPath();
            List<ScannedSourceFile> files = new ArrayList<>();
            long[] totalSize = {0};
            Map<SourceFileType, Integer> counts = new EnumMap<>(SourceFileType.class);

            Files.walkFileTree(realRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(realRoot)
                            && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    SourceFileType.from(path).ifPresent(type -> {
                        try {
                            addSourceFile(realRoot, path, type, files, counts, totalSize);
                        } catch (IOException exception) {
                            throw new SourceScanIOException(exception);
                        }
                    });
                    return FileVisitResult.CONTINUE;
                }
            });
            return files;
        } catch (SourceScanIOException exception) {
            throw new SourceImportException("读取项目源码失败", exception.getCause());
        } catch (IOException exception) {
            throw new SourceImportException("读取Git仓库文件失败", exception);
        }
    }

    private void addSourceFile(
            Path realRoot,
            Path path,
            SourceFileType type,
            List<ScannedSourceFile> files,
            Map<SourceFileType, Integer> counts,
            long[] totalSize
    ) throws IOException {
        if (type == SourceFileType.SQL && !isMigrationSql(realRoot, path)) {
            return;
        }
        Path realFile = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realFile.startsWith(realRoot) || Files.isSymbolicLink(path)) {
            throw new SourceImportException("检测到仓库外部文件，已终止导入");
        }

        long size = Files.size(realFile);
        if (size > properties.getMaxFileSizeBytes()) {
            throw new SourceImportException("文件超过大小限制：" + realRoot.relativize(realFile));
        }
        totalSize[0] += size;
        if (totalSize[0] > properties.getMaxTotalSizeBytes()) {
            throw new SourceImportException("待分析文件总大小超过限制");
        }
        int typeCount = counts.merge(type, 1, Integer::sum);
        if (type == SourceFileType.JAVA && typeCount > properties.getMaxJavaFiles()) {
            throw new SourceImportException("Java文件数量超过限制");
        }
        if (type != SourceFileType.JAVA && configurationFileCount(counts) > properties.getMaxConfigFiles()) {
            throw new SourceImportException("配置文件数量超过限制");
        }
        if (type == SourceFileType.SQL && typeCount > properties.getMaxSchemaFiles()) {
            throw new SourceImportException("数据库迁移文件数量超过限制");
        }

        byte[] content = Files.readAllBytes(realFile);
        String relativePath = realRoot.relativize(realFile)
                .toString()
                .replace(realRoot.getFileSystem().getSeparator(), "/");
        files.add(new ScannedSourceFile(
                realFile.getFileName().toString(),
                relativePath,
                type,
                sha256(relativePath.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sha256(content),
                size,
                realFile
        ));
    }

    private int configurationFileCount(Map<SourceFileType, Integer> counts) {
        return counts.getOrDefault(SourceFileType.YAML, 0)
                + counts.getOrDefault(SourceFileType.PROPERTIES, 0);
    }

    private boolean isMigrationSql(Path root, Path path) {
        String relativePath = root.relativize(path).toString()
                .replace(root.getFileSystem().getSeparator(), "/")
                .toLowerCase(java.util.Locale.ROOT);
        return relativePath.startsWith("db/migration/")
                || relativePath.contains("/db/migration/")
                || relativePath.startsWith("migrations/")
                || relativePath.contains("/migrations/")
                || relativePath.startsWith("database/migrations/")
                || relativePath.contains("/database/migrations/");
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private static final class SourceScanIOException extends RuntimeException {
        private SourceScanIOException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
