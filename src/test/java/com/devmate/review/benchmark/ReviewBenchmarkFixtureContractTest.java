package com.devmate.review.benchmark;

import com.devmate.agent.model.AiFindingCategory;
import com.devmate.review.model.ReviewExpectationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewBenchmarkFixtureContractTest {

    private static final Path DATASET_ROOT = Path.of(
            "benchmarks/review-fixtures/known-defects-v1"
    ).toAbsolutePath().normalize();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void manifestMatchesVersionedBaseAndTargetSnapshots() throws Exception {
        BenchmarkManifest manifest = OBJECT_MAPPER.readValue(
                DATASET_ROOT.resolve("manifest.json").toFile(),
                BenchmarkManifest.class
        );

        assertThat(validate(manifest, DATASET_ROOT)).isEmpty();
        assertThat(manifest.datasetVersion()).isEqualTo(DATASET_ROOT.getFileName().toString());
        assertThat(manifest.scenarios())
                .extracting(BenchmarkScenario::expectationType)
                .contains(ReviewExpectationType.DEFECT, ReviewExpectationType.CLEAN);
        assertThat(manifest.scenarios())
                .flatExtracting(BenchmarkScenario::defects)
                .extracting(BenchmarkDefect::category)
                .contains(
                        AiFindingCategory.CONCURRENCY,
                        AiFindingCategory.TRANSACTION,
                        AiFindingCategory.CACHE,
                        AiFindingCategory.MESSAGE,
                        AiFindingCategory.SQL,
                        AiFindingCategory.SECURITY,
                        AiFindingCategory.PERFORMANCE
                );
    }

    @Test
    void rejectsEscapingPathsAndRangesOutsideTargetFiles() {
        BenchmarkDefect invalidDefect = new BenchmarkDefect(
                "invalid-defect",
                AiFindingCategory.SECURITY,
                "../../private/Secret.java",
                999,
                1000,
                "用于验证清单失败路径"
        );
        BenchmarkScenario invalidScenario = new BenchmarkScenario(
                "security-path-traversal",
                "非法清单",
                ReviewExpectationType.DEFECT,
                "路径不能逃逸样本根目录",
                List.of(invalidDefect)
        );
        BenchmarkManifest invalidManifest = new BenchmarkManifest(
                "known-defects-v1",
                "非法清单",
                List.of(invalidScenario)
        );

        assertThat(validate(invalidManifest, DATASET_ROOT))
                .anyMatch(message -> message.contains("项目内相对路径"));
    }

    private List<String> validate(BenchmarkManifest manifest, Path datasetRoot) {
        List<String> violations = new ArrayList<>();
        Set<String> scenarioKeys = new HashSet<>();
        Set<String> caseKeys = new HashSet<>();
        if (manifest.datasetVersion() == null || manifest.datasetVersion().isBlank()) {
            violations.add("datasetVersion 不能为空");
        }
        if (manifest.scenarios() == null || manifest.scenarios().isEmpty()) {
            violations.add("至少需要一个场景");
            return violations;
        }

        for (BenchmarkScenario scenario : manifest.scenarios()) {
            if (!scenarioKeys.add(scenario.scenarioKey())) {
                violations.add("场景键重复: " + scenario.scenarioKey());
            }
            if (scenario.rationale() == null || scenario.rationale().isBlank()) {
                violations.add("场景缺少人工依据: " + scenario.scenarioKey());
            }
            List<BenchmarkDefect> defects = scenario.defects() == null
                    ? List.of()
                    : scenario.defects();
            if (scenario.expectationType() == ReviewExpectationType.CLEAN && !defects.isEmpty()) {
                violations.add("CLEAN 场景不能包含缺陷: " + scenario.scenarioKey());
            }
            if (scenario.expectationType() == ReviewExpectationType.DEFECT && defects.isEmpty()) {
                violations.add("DEFECT 场景至少需要一个缺陷: " + scenario.scenarioKey());
            }

            Path scenarioRoot = datasetRoot.resolve("scenarios")
                    .resolve(scenario.scenarioKey()).normalize();
            Path baseRoot = scenarioRoot.resolve("base");
            Path targetRoot = scenarioRoot.resolve("candidate");
            validateSnapshotPair(scenario.scenarioKey(), baseRoot, targetRoot, violations);
            for (BenchmarkDefect defect : defects) {
                validateDefect(defect, baseRoot, targetRoot, caseKeys, violations);
            }
        }

        try (Stream<Path> paths = Files.list(datasetRoot.resolve("scenarios"))) {
            Set<String> directoryKeys = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            if (!directoryKeys.equals(scenarioKeys)) {
                violations.add("清单场景与目录不一致");
            }
        } catch (IOException exception) {
            violations.add("无法读取场景目录");
        }
        return violations;
    }

    private void validateSnapshotPair(
            String scenarioKey,
            Path baseRoot,
            Path targetRoot,
            List<String> violations
    ) {
        if (!Files.isDirectory(baseRoot) || !Files.isDirectory(targetRoot)) {
            violations.add("场景缺少 base/target: " + scenarioKey);
            return;
        }
        try {
            Set<Path> baseFiles = relativeFiles(baseRoot);
            Set<Path> targetFiles = relativeFiles(targetRoot);
            if (!baseFiles.equals(targetFiles)) {
                violations.add("base/target 文件集合不一致: " + scenarioKey);
                return;
            }
            boolean changed = false;
            for (Path relative : baseFiles) {
                if (Files.mismatch(baseRoot.resolve(relative), targetRoot.resolve(relative)) != -1) {
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                violations.add("base/target 没有真实变更: " + scenarioKey);
            }
        } catch (IOException exception) {
            violations.add("无法读取场景快照: " + scenarioKey);
        }
    }

    private void validateDefect(
            BenchmarkDefect defect,
            Path baseRoot,
            Path targetRoot,
            Set<String> caseKeys,
            List<String> violations
    ) {
        if (!caseKeys.add(defect.caseKey())) {
            violations.add("标准答案键重复: " + defect.caseKey());
        }
        Path relative = normalizeRelative(defect.filePath(), violations);
        if (relative == null) {
            return;
        }
        Path target = targetRoot.resolve(relative).normalize();
        Path base = baseRoot.resolve(relative).normalize();
        if (!target.startsWith(targetRoot) || !Files.isRegularFile(target)) {
            violations.add("目标文件不存在: " + defect.filePath());
            return;
        }
        if (defect.startLine() < 1 || defect.endLine() < defect.startLine()) {
            violations.add("行范围不合法: " + defect.caseKey());
            return;
        }
        try {
            int lineCount;
            try (Stream<String> lines = Files.lines(target)) {
                lineCount = Math.toIntExact(lines.count());
            }
            if (defect.endLine() > lineCount) {
                violations.add("行范围超出目标文件: " + defect.caseKey());
                return;
            }
            if (!overlapsChangedTargetLine(base, target, defect.startLine(), defect.endLine())) {
                violations.add("标准答案没有覆盖目标版本变更行: " + defect.caseKey());
            }
        } catch (IOException exception) {
            violations.add("无法校验目标文件: " + defect.caseKey());
        }
    }

    private Path normalizeRelative(String value, List<String> violations) {
        try {
            Path normalized = Path.of(value).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                violations.add("缺陷文件路径必须是项目内相对路径: " + value);
                return null;
            }
            return normalized;
        } catch (InvalidPathException exception) {
            violations.add("缺陷文件路径格式不合法: " + value);
            return null;
        }
    }

    private boolean overlapsChangedTargetLine(
            Path base,
            Path target,
            int startLine,
            int endLine
    ) throws IOException {
        RawText before = new RawText(Files.readAllBytes(base));
        RawText after = new RawText(Files.readAllBytes(target));
        EditList edits = new HistogramDiff().diff(RawTextComparator.DEFAULT, before, after);
        for (Edit edit : edits) {
            int changedStart = edit.getBeginB() + 1;
            int changedEnd = Math.max(changedStart, edit.getEndB());
            if (startLine <= changedEnd && endLine >= changedStart) {
                return true;
            }
        }
        return false;
    }

    private Set<Path> relativeFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private record BenchmarkManifest(
            String datasetVersion,
            String description,
            List<BenchmarkScenario> scenarios
    ) {
    }

    private record BenchmarkScenario(
            String scenarioKey,
            String name,
            ReviewExpectationType expectationType,
            String rationale,
            List<BenchmarkDefect> defects
    ) {
    }

    private record BenchmarkDefect(
            String caseKey,
            AiFindingCategory category,
            String filePath,
            int startLine,
            int endLine,
            String rationale
    ) {
    }
}
