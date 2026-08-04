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
                "src/main/resources/db/migration/V1__init.sql"
        );
        assertThat(files).extracting(ScannedSourceFile::fileType).containsExactlyInAnyOrder(
                SourceFileType.JAVA, SourceFileType.YAML, SourceFileType.PROPERTIES, SourceFileType.SQL
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
