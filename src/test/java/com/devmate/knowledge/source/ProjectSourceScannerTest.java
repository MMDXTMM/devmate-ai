package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSourceScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansJavaAndConfigurationFilesAndIgnoresBuildDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.createDirectories(tempDir.resolve("src/main/resources/db/migration"));
        Files.createDirectories(tempDir.resolve("target/generated"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "class App {}\n");
        Files.writeString(tempDir.resolve("src/main/resources/application.yml"), "server:\n  port: 8080\n");
        Files.writeString(tempDir.resolve("src/main/resources/review.properties"), "review.limit=20\n");
        Files.writeString(tempDir.resolve("src/main/resources/db/migration/V1__init.sql"),
                "CREATE TABLE demo (id BIGINT);\n");
        Files.writeString(tempDir.resolve("ad-hoc.sql"), "SELECT * FROM secret_data;\n");
        Files.writeString(tempDir.resolve("target/generated/Ignored.java"), "class Ignored {}\n");
        Files.writeString(tempDir.resolve("README.md"), "demo");

        ProjectSourceScanner scanner = new ProjectSourceScanner(defaultProperties());
        List<ScannedSourceFile> files = scanner.scan(tempDir);

        assertThat(files).extracting(ScannedSourceFile::relativePath).containsExactlyInAnyOrder(
                "src/main/java/com/example/App.java",
                "src/main/resources/application.yml",
                "src/main/resources/review.properties",
                "src/main/resources/db/migration/V1__init.sql", "README.md"
        );
        assertThat(files).extracting(ScannedSourceFile::fileType).containsExactlyInAnyOrder(
                SourceFileType.JAVA, SourceFileType.YAML, SourceFileType.PROPERTIES, SourceFileType.SQL, SourceFileType.DOCUMENTATION
        );
        assertThat(files).allSatisfy(file -> {
            assertThat(file.contentHash()).hasSize(64);
            assertThat(file.pathHash()).hasSize(64);
        });
    }

    @Test
    void rejectsFileAboveConfiguredSize() throws Exception {
        Files.writeString(tempDir.resolve("Large.java"), "class Large {}\n");
        SourceImportProperties properties = defaultProperties();
        properties.setMaxFileSizeBytes(4);

        ProjectSourceScanner scanner = new ProjectSourceScanner(properties);

        assertThatThrownBy(() -> scanner.scan(tempDir))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("超过大小限制");
    }

    @Test
    void rejectsTooManyConfigurationFiles() throws Exception {
        Files.writeString(tempDir.resolve("a.yml"), "a: 1\n");
        Files.writeString(tempDir.resolve("b.properties"), "b=2\n");
        SourceImportProperties properties = defaultProperties();
        properties.setMaxConfigFiles(1);

        assertThatThrownBy(() -> new ProjectSourceScanner(properties).scan(tempDir))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("配置文件数量超过限制");
    }

    @Test
    void rejectsTooManyMigrationFiles() throws Exception {
        Path migrations = tempDir.resolve("src/main/resources/db/migration");
        Files.createDirectories(migrations);
        Files.writeString(migrations.resolve("V1__one.sql"), "CREATE TABLE one (id BIGINT);\n");
        Files.writeString(migrations.resolve("V2__two.sql"), "CREATE TABLE two (id BIGINT);\n");
        SourceImportProperties properties = defaultProperties();
        properties.setMaxSchemaFiles(1);

        assertThatThrownBy(() -> new ProjectSourceScanner(properties).scan(tempDir))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("数据库迁移文件数量超过限制");
    }

    @Test
    void readsNonJavaProjectsButSkipsSensitiveDependenciesAndBinaryDisguises() throws Exception {
        Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.writeString(tempDir.resolve("node_modules/pkg/index.ts"), "ignored");
        Files.writeString(tempDir.resolve("main.py"), "print('hello')");
        Files.writeString(tempDir.resolve("index.ts"), "export const demo = true");
        Files.writeString(tempDir.resolve("package.json"), "{}");
        Files.writeString(tempDir.resolve("README.md"), "a CLI tool");
        Files.writeString(tempDir.resolve(".env.json"), "sensitive");
        Files.writeString(tempDir.resolve("credentials.json"), "sensitive");
        Files.writeString(tempDir.resolve("application-local.yml"), "sensitive");
        Files.write(tempDir.resolve("binary.ts"), new byte[] {0, 1, 2});
        Files.write(tempDir.resolve("invalid.py"), new byte[] {(byte)0xff});
        var files = new ProjectSourceScanner(defaultProperties()).scan(tempDir);
        assertThat(files).extracting(ScannedSourceFile::relativePath).containsExactlyInAnyOrder("main.py", "index.ts", "package.json", "README.md");
        var properties = defaultProperties(); properties.setMaxTextFiles(1);
        assertThatThrownBy(() -> new ProjectSourceScanner(properties).scan(tempDir)).hasMessageContaining("文本文件数量超过限制");
    }

    @Test
    void textExcerptsKeepRealLinesAndRedactLiteralKeys() throws Exception {
        String source = "# report\napi_key = 'example-sensitive-value'\nprint('ok')\n" + "line\n".repeat(65);
        Files.writeString(tempDir.resolve("main.py"), source);
        var file = new ProjectSourceScanner(defaultProperties()).scan(tempDir).getFirst();
        var parsed = new TextSourceParser().parse(file);
        assertThat(parsed.references()).isEmpty();
        assertThat(parsed.chunks()).hasSize(2);
        assertThat(parsed.chunks().getFirst().content()).contains("[REDACTED]", "print('ok')").doesNotContain("example-sensitive-value");
        assertThat(parsed.chunks().getFirst().startLine()).isEqualTo(1);
        assertThat(parsed.chunks().getFirst().endLine()).isEqualTo(60);
        assertThat(parsed.chunks().get(1).startLine()).isEqualTo(61);
    }

    @Test
    void rejectsSymlinkOutsideRepository() throws Exception {
        Path outside = Files.createTempFile("guide-outside", ".py");
        try {
            Files.writeString(outside, "private");
            Files.createSymbolicLink(tempDir.resolve("escape.py"), outside);
            assertThatThrownBy(() -> new ProjectSourceScanner(defaultProperties()).scan(tempDir))
                    .hasMessageContaining("外部文件");
        } finally { Files.deleteIfExists(outside); }
    }

    private SourceImportProperties defaultProperties() {
        SourceImportProperties properties = new SourceImportProperties();
        properties.setMaxJavaFiles(10);
        properties.setMaxConfigFiles(10);
        properties.setMaxSchemaFiles(10);
        properties.setMaxFileSizeBytes(1024);
        properties.setMaxTotalSizeBytes(4096);
        return properties;
    }
}
