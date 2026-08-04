package com.devmate.review.source;

import com.devmate.review.model.GitChangedFile;
import com.devmate.review.model.GitDiffResult;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitDiffAnalyzerTest {

    @TempDir
    Path tempDir;

    private final GitDiffAnalyzer analyzer = new GitDiffAnalyzer();

    @Test
    void analyzesAddedModifiedAndDeletedFilesWithoutDroppingCoverage() throws Exception {
        String base;
        String target;
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            configureUser(git);
            Files.writeString(tempDir.resolve("App.java"), "class App { void run() {} }\n");
            Files.writeString(tempDir.resolve("Old.java"), "class Old {}\n");
            git.add().addFilepattern(".").call();
            base = git.commit().setMessage("base").call().name();

            Files.writeString(tempDir.resolve("App.java"), """
                    class App {
                        void run() {
                            System.out.println("changed");
                        }
                    }
                    """);
            Files.delete(tempDir.resolve("Old.java"));
            Files.writeString(tempDir.resolve("application.yml"), "feature: true\n");
            git.add().addFilepattern(".").call();
            git.rm().addFilepattern("Old.java").call();
            target = git.commit().setMessage("target").call().name();
        }

        GitDiffResult result = analyzer.analyze(tempDir, null, null);
        Map<String, GitChangedFile> files = result.files().stream()
                .collect(Collectors.toMap(
                        file -> file.newPath() == null ? file.oldPath() : file.newPath(),
                        Function.identity()
                ));

        assertThat(result.baseRevision()).isEqualTo(base);
        assertThat(result.targetRevision()).isEqualTo(target);
        assertThat(files).containsOnlyKeys("App.java", "Old.java", "application.yml");
        assertThat(files.get("App.java").changeType()).isEqualTo("MODIFY");
        assertThat(files.get("App.java").targetLineRanges()).isNotEmpty();
        assertThat(files.get("Old.java").changeType()).isEqualTo("DELETE");
        assertThat(files.get("Old.java").newPath()).isNull();
        assertThat(files.get("application.yml").changeType()).isEqualTo("ADD");
    }

    @Test
    void rejectsRepositoryWithOnlyOneCommitWhenBaseIsImplicit() throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            configureUser(git);
            Files.writeString(tempDir.resolve("App.java"), "class App {}\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();
        }

        assertThatThrownBy(() -> analyzer.analyze(tempDir, null, null))
                .hasMessageContaining("没有父提交");
    }

    @Test
    void detectsExactJavaFileRename() throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            configureUser(git);
            String source = """
                    package com.example;
                    class RenamedService {
                        void execute() {
                            System.out.println("stable content");
                        }
                    }
                    """;
            Files.writeString(tempDir.resolve("OldName.java"), source);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("base").call();

            Files.move(tempDir.resolve("OldName.java"), tempDir.resolve("NewName.java"));
            git.add().addFilepattern("NewName.java").call();
            git.rm().addFilepattern("OldName.java").call();
            git.commit().setMessage("rename").call();
        }

        GitDiffResult result = analyzer.analyze(tempDir, null, null);

        assertThat(result.files()).singleElement().satisfies(file -> {
            assertThat(file.changeType()).isEqualTo("RENAME");
            assertThat(file.oldPath()).isEqualTo("OldName.java");
            assertThat(file.newPath()).isEqualTo("NewName.java");
        });
    }

    private void configureUser(Git git) throws Exception {
        git.getRepository().getConfig().setString("user", null, "name", "DevMate Test");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        git.getRepository().getConfig().save();
    }
}
