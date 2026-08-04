package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Component
public class JavaSourceScanner {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", "target", "build", "out", "node_modules"
    );

    private final SourceImportProperties properties;

    public JavaSourceScanner(SourceImportProperties properties) {
        this.properties = properties;
    }

    public List<ScannedSourceFile> scan(Path repositoryRoot) {
        try {
            Path realRoot = repositoryRoot.toRealPath();
            List<ScannedSourceFile> files = new ArrayList<>();
            long[] totalSize = {0};

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
                    if (isJavaSource(path)) {
                        addSourceFile(realRoot, path, files, totalSize);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return files;
        } catch (IOException exception) {
            throw new SourceImportException("读取Git仓库文件失败", exception);
        }
    }

    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().endsWith(".java");
    }

    private void addSourceFile(
            Path realRoot,
            Path path,
            List<ScannedSourceFile> files,
            long[] totalSize
    ) throws IOException {
        Path realFile = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realFile.startsWith(realRoot)) {
            throw new SourceImportException("检测到仓库外部文件，已终止导入");
        }

        long size = Files.size(realFile);
        if (size > properties.getMaxFileSizeBytes()) {
            throw new SourceImportException("Java文件超过大小限制：" + realRoot.relativize(realFile));
        }
        totalSize[0] += size;
        if (totalSize[0] > properties.getMaxTotalSizeBytes()) {
            throw new SourceImportException("Java源码总大小超过限制");
        }
        if (files.size() >= properties.getMaxJavaFiles()) {
            throw new SourceImportException("Java文件数量超过限制");
        }

        byte[] content = Files.readAllBytes(realFile);
        String relativePath = realRoot.relativize(realFile)
                .toString()
                .replace(realRoot.getFileSystem().getSeparator(), "/");
        files.add(new ScannedSourceFile(
                realFile.getFileName().toString(),
                relativePath,
                sha256(relativePath.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sha256(content),
                size,
                realFile
        ));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
