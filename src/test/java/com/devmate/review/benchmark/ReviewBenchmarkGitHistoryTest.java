package com.devmate.review.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewBenchmarkGitHistoryTest {

    private static final Path DATASET_ROOT = Path.of(
            "benchmarks/review-fixtures/known-defects-v1"
    ).toAbsolutePath().normalize();
    private static final Path GENERATED_REPOSITORY = Path.of(
            "target/review-benchmark-repository"
    ).toAbsolutePath().normalize();
    private static final Path GENERATED_REVISIONS = Path.of(
            "target/review-benchmark-revisions.json"
    ).toAbsolutePath().normalize();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void buildsDeterministicTwoCommitScenarioBranches() throws Exception {
        ReviewBenchmarkRepositoryBuilder.deleteDirectory(GENERATED_REPOSITORY);
        ReviewBenchmarkRepositoryBuilder builder = new ReviewBenchmarkRepositoryBuilder();
        ReviewBenchmarkRepositoryBuilder.BuildResult first = builder.build(
                DATASET_ROOT,
                GENERATED_REPOSITORY
        );
        Files.createDirectories(GENERATED_REVISIONS.getParent());
        OBJECT_MAPPER.writeValue(GENERATED_REVISIONS.toFile(), first);

        assertThat(first.datasetVersion()).isEqualTo("known-defects-v1");
        assertThat(first.repositoryUrl())
                .isEqualTo("https://github.com/MMDXTMM/devmate-review-benchmark.git");
        assertThat(first.scenarios()).hasSize(8);
        assertRepositoryHistory(first);

        Path secondRepository = GENERATED_REPOSITORY.resolveSibling(
                "review-benchmark-repository-repeat"
        );
        ReviewBenchmarkRepositoryBuilder.deleteDirectory(secondRepository);
        ReviewBenchmarkRepositoryBuilder.BuildResult second = builder.build(
                DATASET_ROOT,
                secondRepository
        );
        assertThat(second).isEqualTo(first);

        JsonNode expected = OBJECT_MAPPER.readTree(
                DATASET_ROOT.resolve("revisions.json").toFile()
        );
        JsonNode actual = OBJECT_MAPPER.valueToTree(first);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void refusesToOverwriteNonEmptyDestination() throws Exception {
        Path nonEmpty = Path.of("target/review-benchmark-non-empty")
                .toAbsolutePath().normalize();
        ReviewBenchmarkRepositoryBuilder.deleteDirectory(nonEmpty);
        Files.createDirectories(nonEmpty);
        Files.writeString(nonEmpty.resolve("keep.txt"), "do not overwrite");

        ReviewBenchmarkRepositoryBuilder builder = new ReviewBenchmarkRepositoryBuilder();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> builder.build(DATASET_ROOT, nonEmpty)
                )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("输出目录必须为空");
        assertThat(Files.readString(nonEmpty.resolve("keep.txt")))
                .isEqualTo("do not overwrite");
    }

    private void assertRepositoryHistory(
            ReviewBenchmarkRepositoryBuilder.BuildResult result
    ) throws Exception {
        try (Git git = Git.open(GENERATED_REPOSITORY.toFile())) {
            for (ReviewBenchmarkRepositoryBuilder.ScenarioRevision scenario : result.scenarios()) {
                ObjectId branchHead = git.getRepository().resolve(
                        "refs/heads/" + scenario.repositoryBranch()
                );
                assertThat(branchHead.name()).isEqualTo(scenario.candidateRevision());

                try (RevWalk walk = new RevWalk(git.getRepository())) {
                    RevCommit candidate = walk.parseCommit(branchHead);
                    assertThat(candidate.getParentCount()).isEqualTo(1);
                    RevCommit base = walk.parseCommit(candidate.getParent(0));
                    assertThat(base.name()).isEqualTo(scenario.baseRevision());
                    assertThat(base.getParentCount()).isEqualTo(1);
                    assertThat(base.getParent(0).name())
                            .isEqualTo(git.getRepository().resolve("refs/heads/main").name());

                    List<DiffEntry> changes = diff(git, base, candidate);
                    assertThat(changes).hasSize(1);
                    assertThat(changes.getFirst().getChangeType())
                            .isEqualTo(DiffEntry.ChangeType.MODIFY);
                    assertThat(changes.getFirst().getNewPath()).startsWith("src/main/java/");
                }
            }
        }
    }

    private List<DiffEntry> diff(Git git, RevCommit base, RevCommit candidate) throws Exception {
        CanonicalTreeParser oldTree = new CanonicalTreeParser();
        CanonicalTreeParser newTree = new CanonicalTreeParser();
        try (var reader = git.getRepository().newObjectReader()) {
            oldTree.reset(reader, base.getTree());
            newTree.reset(reader, candidate.getTree());
        }
        return git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call();
    }
}
