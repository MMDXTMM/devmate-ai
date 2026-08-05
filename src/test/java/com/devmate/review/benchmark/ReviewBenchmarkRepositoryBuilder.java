package com.devmate.review.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ReviewBenchmarkRepositoryBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final String AUTHOR_NAME = "DevMate Benchmark";
    private static final String AUTHOR_EMAIL = "benchmark@devmate.local";

    BuildResult build(Path datasetRoot, Path repositoryRoot) throws Exception {
        requireEmptyDestination(repositoryRoot);
        Files.createDirectories(repositoryRoot);
        Manifest manifest = OBJECT_MAPPER.readValue(
                datasetRoot.resolve("manifest.json").toFile(),
                Manifest.class
        );

        List<ScenarioRevision> revisions = new ArrayList<>();
        try (Git git = Git.init()
                .setDirectory(repositoryRoot.toFile())
                .setInitialBranch("main")
                .call()) {
            git.getRepository().getConfig().setBoolean("core", null, "autocrlf", false);
            git.getRepository().getConfig().save();
            Files.writeString(
                    repositoryRoot.resolve("README.md"),
                    "# DevMate Review Benchmark\n\nSynthetic Java changes for repeatable code review evaluation.\n"
            );
            git.add().addFilepattern("README.md").call();
            commit(git, "fixture: initialize benchmark repository", EPOCH);

            for (int index = 0; index < manifest.scenarios().size(); index++) {
                Scenario scenario = manifest.scenarios().get(index);
                git.checkout()
                        .setCreateBranch(true)
                        .setName(scenario.repositoryBranch())
                        .setStartPoint("main")
                        .call();

                Path scenarioRoot = datasetRoot.resolve("scenarios")
                        .resolve(scenario.scenarioKey());
                copySnapshot(scenarioRoot.resolve("base"), repositoryRoot);
                git.add().addFilepattern(".").call();
                ObjectId baseRevision = commit(
                        git,
                        "fixture: add base snapshot " + scenario.repositoryBranch(),
                        EPOCH.plusSeconds(index * 120L + 60L)
                );

                copySnapshot(scenarioRoot.resolve("candidate"), repositoryRoot);
                git.add().addFilepattern(".").call();
                ObjectId candidateRevision = commit(
                        git,
                        "fixture: add candidate snapshot " + scenario.repositoryBranch(),
                        EPOCH.plusSeconds(index * 120L + 120L)
                );
                revisions.add(new ScenarioRevision(
                        scenario.scenarioKey(),
                        scenario.repositoryBranch(),
                        baseRevision.name(),
                        candidateRevision.name()
                ));

                git.checkout().setName("main").call();
            }
        }
        return new BuildResult(
                manifest.datasetVersion(),
                manifest.repositoryUrl(),
                List.copyOf(revisions)
        );
    }

    private ObjectId commit(Git git, String message, Instant instant) throws Exception {
        PersonIdent identity = new PersonIdent(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                instant,
                ZoneOffset.UTC
        );
        return git.commit()
                .setMessage(message)
                .setAuthor(identity)
                .setCommitter(identity)
                .call()
                .getId();
    }

    private void copySnapshot(Path snapshotRoot, Path repositoryRoot) throws IOException {
        if (!Files.isDirectory(snapshotRoot)) {
            throw new IllegalArgumentException("样本快照不存在: " + snapshotRoot);
        }
        try (Stream<Path> paths = Files.walk(snapshotRoot)) {
            for (Path source : paths.sorted().toList()) {
                Path target = repositoryRoot.resolve(snapshotRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private void requireEmptyDestination(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.list(root)) {
            if (paths.findAny().isPresent()) {
                throw new IllegalArgumentException("输出目录必须为空: " + root);
            }
        }
    }

    record BuildResult(
            String datasetVersion,
            String repositoryUrl,
            List<ScenarioRevision> scenarios
    ) {
    }

    record ScenarioRevision(
            String scenarioKey,
            String repositoryBranch,
            String baseRevision,
            String candidateRevision
    ) {
    }

    private record Manifest(
            String datasetVersion,
            String repositoryUrl,
            List<Scenario> scenarios
    ) {
    }

    private record Scenario(String scenarioKey, String repositoryBranch) {
    }
}
