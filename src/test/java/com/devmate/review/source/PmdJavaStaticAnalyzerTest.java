package com.devmate.review.source;

import com.devmate.review.model.LineRange;
import com.devmate.review.model.StaticAnalysisTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PmdJavaStaticAnalyzerTest {

    @TempDir
    Path tempDir;

    private final PmdJavaStaticAnalyzer analyzer = new PmdJavaStaticAnalyzer();

    @Test
    void returnsOnlyViolationsThatIntersectChangedLines() throws Exception {
        Path source = tempDir.resolve("ReviewTarget.java");
        Files.writeString(source, """
                class ReviewTarget {
                    void run() {
                        try {
                            System.out.println("work");
                        } catch (RuntimeException exception) {
                        }
                    }
                }
                """);

        var result = analyzer.analyze(tempDir, List.of(new StaticAnalysisTarget(
                "ReviewTarget.java",
                source,
                List.of(new LineRange(1, 8))
        )));

        assertThat(result.toolName()).isEqualTo("PMD");
        assertThat(result.analyzedFiles()).isEqualTo(1);
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.ruleId()).isEqualTo("EmptyCatchBlock");
            assertThat(finding.filePath()).isEqualTo("ReviewTarget.java");
            assertThat(finding.category()).isEqualTo("ERROR_HANDLING");
            assertThat(finding.startLine()).isPositive();
        });
    }

    @Test
    void filtersExistingViolationsOutsideChangedLines() throws Exception {
        Path source = tempDir.resolve("ExistingIssue.java");
        Files.writeString(source, """
                class ExistingIssue {
                    void run() {
                        try {
                        } catch (RuntimeException exception) {
                        }
                    }
                }
                """);

        var result = analyzer.analyze(tempDir, List.of(new StaticAnalysisTarget(
                "ExistingIssue.java",
                source,
                List.of(new LineRange(1, 1))
        )));

        assertThat(result.findings()).isEmpty();
    }
}
