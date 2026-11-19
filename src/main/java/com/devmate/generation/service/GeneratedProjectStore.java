package com.devmate.generation.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.generation.dto.GenerationRunResponse.FileEntry;
import com.devmate.generation.entity.GenerationRun;
import com.devmate.generation.model.ProjectBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class GeneratedProjectStore {
    private static final Set<String> SOURCE_EXTENSIONS = Set.of("py", "js", "ts", "tsx", "jsx", "vue", "html", "java", "kt", "go", "rs", "cs", "rb", "php", "swift", "dart", "c", "cpp", "h", "m", "ex", "exs", "scala", "r", "lua", "sh");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("css", "scss", "json", "md", "sql", "xml", "properties", "yml", "yaml", "toml", "txt", "ini", "cfg", "conf", "svelte", "astro", "csproj", "sln", "gradle", "kts", "bat", "ps1", "csv", "svg", "prisma", "env", "example", "lock", "mod", "sum");
    private static final Set<String> FORBIDDEN_PARTS = Set.of(".git", "node_modules", "target", "dist", ".env", ".ssh", ".devmate-local-secrets");
    private static final String REPORT = "DEVMATE_GENERATION.md";
    private final Path root;

    public GeneratedProjectStore(@Value("${devmate.generation.workspace-root:${user.dir}/workspace/generated}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public List<FileEntry> write(GenerationRun run, ProjectBundle bundle) throws IOException {
        validate(bundle);
        Path directory = directory(run);
        List<ProjectBundle.ProjectFile> files = new ArrayList<>(bundle.files());
        files.add(new ProjectBundle.ProjectFile(REPORT, "# 生成检查记录\n\n已按确认方案生成文本源码，并通过路径、大小、文件重复和基本交付物检查。\n\n未安装依赖、未执行代码、编译或测试；不代表运行正确、生产可用。请先阅读README并在自己的隔离环境运行测试。\n"));
        List<FileEntry> manifest = new ArrayList<>();
        for (var file : files) {
            Path destination = directory.resolve("files").resolve(file.path()).normalize();
            safeDirectories(destination.getParent());
            if (Files.isSymbolicLink(destination)) throw invalid("拒绝写入符号链接");
            Files.writeString(destination, file.content(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            manifest.add(new FileEntry(file.path(), file.content().length()));
        }
        try (var output = new ZipOutputStream(Files.newOutputStream(directory.resolve("bundle.zip"), StandardOpenOption.CREATE_NEW))) {
            for (var file : files) {
                output.putNextEntry(new ZipEntry(file.path()));
                output.write(file.content().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return List.copyOf(manifest);
    }

    public String read(GenerationRun run, String path) throws IOException {
        validatePath(path);
        Path file = directory(run).resolve("files").resolve(path);
        rejectLinks(file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }
    public byte[] download(GenerationRun run) throws IOException {
        Path zip = directory(run).resolve("bundle.zip");
        rejectLinks(zip);
        return Files.readAllBytes(zip);
    }

    public void validate(ProjectBundle bundle) {
        if (bundle == null || bundle.files() == null || bundle.files().size() < 2 || bundle.files().size() > 60
                || bundle.summary() == null || bundle.summary().isBlank() || bundle.summary().length() > 8000
                || bundle.runInstructions() == null || bundle.runInstructions().isBlank() || bundle.runInstructions().length() > 16000
                || bundle.warnings() == null || bundle.warnings().size() > 20) throw invalid("生成结果缺少源码、说明或超出交付预算");
        Set<String> paths = new HashSet<>();
        int total = 0;
        boolean source = false;
        for (var file : bundle.files()) {
            if (file == null) throw invalid("生成文件为空");
            validatePath(file.path());
            String lower = file.path().toLowerCase(Locale.ROOT);
            String extension = lower.substring(lower.lastIndexOf('.') + 1);
            if (file.path().equalsIgnoreCase(REPORT) || !paths.add(lower)) throw invalid("生成文件路径重复或使用保留名称");
            if (!SOURCE_EXTENSIONS.contains(extension) && !TEXT_EXTENSIONS.contains(extension)
                    && !Set.of("makefile", "dockerfile", ".gitignore", "gemfile", "procfile", ".npmrc", "license").contains(lower.substring(lower.lastIndexOf('/') + 1))) {
                throw invalid("生成结果包含不支持的文件类型");
            }
            if (file.content() == null || file.content().isBlank() || file.content().length() > 50000 || file.content().indexOf('\0') >= 0) throw invalid("生成文件为空或超过单文件大小限制");
            total += file.content().length();
            if (total > 180000) throw invalid("生成项目超出文本大小预算，请缩小第一版范围");
            source |= SOURCE_EXTENSIONS.contains(extension);
        }
        if (!paths.contains("readme.md") || !source) throw invalid("生成结果必须包含README.md和实际源码，不能只交付文档");
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 240 || path.startsWith("/") || path.contains("\\")
                || path.contains(":") || path.chars().anyMatch(c -> c < 32) || path.endsWith("/") || path.contains("//")) throw invalid("生成文件路径不合法");
        for (String part : path.split("/")) {
            if (part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || FORBIDDEN_PARTS.contains(part.toLowerCase(Locale.ROOT))) throw invalid("生成文件路径超出安全范围");
        }
    }
    private Path directory(GenerationRun run) {
        if (run.getAttemptKey() == null || !run.getAttemptKey().matches("[A-Za-z0-9_-]{16,80}")) throw invalid("生成任务标识不合法");
        return root.resolve(run.getSessionId().toString()).resolve(run.getId().toString()).resolve(run.getAttemptKey());
    }
    private void safeDirectories(Path path) throws IOException {
        rejectLinks(path);
        Files.createDirectories(path);
        rejectLinks(path);
    }
    private void rejectLinks(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) throw invalid("文件超出生成目录");
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw invalid("生成目录包含符号链接");
        }
    }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.INVALID_ARGUMENT, message); }
}
