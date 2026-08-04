package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSourceScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansJavaSourcesAndIgnoresBuildDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.createDirectories(tempDir.resolve("target/generated"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "class App {}\n");
        Files.writeString(tempDir.resolve("target/generated/Ignored.java"), "class Ignored {}\n");
        Files.writeString(tempDir.resolve("README.md"), "demo");

        JavaSourceScanner scanner = new JavaSourceScanner(defaultProperties());
        List<ScannedSourceFile> files = scanner.scan(tempDir);

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().relativePath()).isEqualTo("src/main/java/com/example/App.java");
        assertThat(files.getFirst().contentHash()).hasSize(64);
        assertThat(files.getFirst().pathHash()).hasSize(64);
    }

    @Test
    void rejectsFileAboveConfiguredSize() throws Exception {
        Files.writeString(tempDir.resolve("Large.java"), "class Large {}\n");
        SourceImportProperties properties = defaultProperties();
        properties.setMaxFileSizeBytes(4);

        JavaSourceScanner scanner = new JavaSourceScanner(properties);

        assertThatThrownBy(() -> scanner.scan(tempDir))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("超过大小限制");
    }

    private SourceImportProperties defaultProperties() {
        SourceImportProperties properties = new SourceImportProperties();
        properties.setMaxJavaFiles(10);
        properties.setMaxFileSizeBytes(1024);
        properties.setMaxTotalSizeBytes(4096);
        return properties;
    }
}
