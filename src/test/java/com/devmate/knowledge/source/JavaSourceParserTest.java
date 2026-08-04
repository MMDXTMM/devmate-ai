package com.devmate.knowledge.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSourceParserTest {

    @TempDir
    Path tempDir;

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void parsesPackageTypesMethodsAnnotationsAndLines() throws Exception {
        String content = """
                package com.example.review;

                @Deprecated
                public class ReviewService {

                    public ReviewService() {
                    }

                    @Override
                    public String toString() {
                        return "review";
                    }

                    static class Result {
                        void complete(int count, String name) {
                        }
                    }
                }
                """;
        Path sourcePath = tempDir.resolve("ReviewService.java");
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8);

        ParsedSourceFile parsed = parser.parse(sourceFile(sourcePath, content));

        assertThat(parsed.packageName()).isEqualTo("com.example.review");
        assertThat(parsed.chunks())
                .extracting(ParsedSourceChunk::symbolName)
                .containsExactly(
                        "com.example.review.ReviewService",
                        "com.example.review.ReviewService#ReviewService()",
                        "com.example.review.ReviewService#toString()",
                        "com.example.review.ReviewService.Result",
                        "com.example.review.ReviewService.Result#complete(int,String)"
                );
        assertThat(parsed.chunks().getFirst().annotations()).containsExactly("Deprecated");
        assertThat(parsed.chunks().get(2).annotations()).containsExactly("Override");
        assertThat(parsed.chunks().get(2).startLine()).isEqualTo(9);
        assertThat(parsed.chunks().get(2).endLine()).isEqualTo(12);
        assertThat(parsed.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.contentHash()).hasSize(64);
            assertThat(chunk.endLine()).isGreaterThanOrEqualTo(chunk.startLine());
        });
    }

    @Test
    void rejectsInvalidJavaSyntaxWithFileAndLine() throws Exception {
        String content = "class Broken { void run( { }";
        Path sourcePath = tempDir.resolve("Broken.java");
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(sourceFile(sourcePath, content)))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("Broken.java")
                .hasMessageContaining("第1行");
    }

    private ScannedSourceFile sourceFile(Path path, String content) {
        return new ScannedSourceFile(
                path.getFileName().toString(),
                path.getFileName().toString(),
                "path-hash",
                Integer.toHexString(content.hashCode()),
                content.getBytes(StandardCharsets.UTF_8).length,
                path
        );
    }
}
