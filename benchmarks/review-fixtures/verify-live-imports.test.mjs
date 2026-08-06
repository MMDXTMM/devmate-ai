import assert from "node:assert/strict";
import test from "node:test";

import {
  DevMateClient,
  VerificationError,
  mergeScenarioDefinitions,
  parseArguments,
  selectExistingProject,
  verifyScenarioEvidence,
  verifyOneScenario,
} from "./verify-live-imports.mjs";

const scenario = {
  scenarioKey: "sample",
  repositoryBranch: "case-001",
  repositoryUrl: "https://github.com/example/fixtures.git",
  datasetVersion: "known-defects-v1",
  baseRevision: "a".repeat(40),
  candidateRevision: "b".repeat(40),
  expectedFilePath: "src/main/java/example/Sample.java",
  projectName: "benchmark-known-defects-v1-case-001",
  defects: [{ startLine: 10, endLine: 12 }],
};

function evidence(overrides = {}) {
  const project = {
    id: "101",
    status: "READY",
    currentRevision: scenario.candidateRevision,
  };
  const importTask = {
    id: "201",
    projectId: project.id,
    status: "SUCCEEDED",
    revision: scenario.candidateRevision,
    totalFiles: 1,
    processedFiles: 1,
    failedFiles: 0,
  };
  const reviewDiff = {
    id: "301",
    projectId: project.id,
    status: "SUCCEEDED",
    baseRevision: scenario.baseRevision,
    targetRevision: scenario.candidateRevision,
    changedFiles: 1,
    fullyMappedFiles: 1,
    partiallyMappedFiles: 0,
    skippedFiles: 0,
    files: [{
      oldPath: scenario.expectedFilePath,
      newPath: scenario.expectedFilePath,
      changeType: "MODIFY",
      coverageStatus: "FULL",
      baseChangedLines: [{ startLine: 9, endLine: 13 }],
      changedLines: [{ startLine: 9, endLine: 13 }],
      mappedSymbols: [
        {
          chunkId: "401",
          revisionSide: "TARGET",
          startLine: 9,
          endLine: 13,
        },
        {
          revisionSide: "BASE",
          startLine: 9,
          endLine: 13,
        },
      ],
    }],
  };
  return {
    project,
    importTask,
    latestImportTask: { ...importTask },
    reviewDiff,
    latestReviewDiff: { ...reviewDiff },
    ...overrides,
  };
}

test("merges answer and revision manifests into one scenario plan", () => {
  const manifest = {
    datasetVersion: "v1",
    repositoryUrl: "https://github.com/example/repo.git",
    scenarios: [{
      scenarioKey: "sample",
      repositoryBranch: "case-001",
      expectationType: "CLEAN",
      defects: [],
    }],
  };
  const revisions = {
    datasetVersion: "v1",
    repositoryUrl: manifest.repositoryUrl,
    scenarios: [{
      scenarioKey: "sample",
      repositoryBranch: "case-001",
      baseRevision: "a".repeat(40),
      candidateRevision: "b".repeat(40),
    }],
  };

  const result = mergeScenarioDefinitions(
    manifest,
    revisions,
    new Map([["sample", [scenario.expectedFilePath]]]),
  );

  assert.equal(result.length, 1);
  assert.equal(result[0].expectedFilePath, scenario.expectedFilePath);
  assert.equal(result[0].projectName, "benchmark-v1-case-001");
});

test("rejects a branch mismatch between the two manifests", () => {
  const manifest = {
    datasetVersion: "v1",
    repositoryUrl: "https://github.com/example/repo.git",
    scenarios: [{ scenarioKey: "sample", repositoryBranch: "case-001", defects: [] }],
  };
  const revisions = {
    datasetVersion: "v1",
    repositoryUrl: manifest.repositoryUrl,
    scenarios: [{ scenarioKey: "sample", repositoryBranch: "case-002" }],
  };

  assert.throws(
    () => mergeScenarioDefinitions(manifest, revisions, new Map([["sample", [scenario.expectedFilePath]]])),
    /Branch mismatch/,
  );
});

test("reuses only one project with the exact repository and branch", () => {
  const project = {
    name: scenario.projectName,
    sourceType: "GIT",
    sourceLocation: scenario.repositoryUrl,
    defaultBranch: scenario.repositoryBranch,
  };

  assert.equal(selectExistingProject([project], scenario), project);
  assert.equal(selectExistingProject([], scenario), null);
  assert.throws(() => selectExistingProject([project, project], scenario), /Multiple projects/);
  assert.throws(
    () => selectExistingProject([{ ...project, defaultBranch: "case-002" }], scenario),
    /different repository branch/,
  );
});

test("accepts full evidence and checks the gold range against changed lines", () => {
  const result = verifyScenarioEvidence(scenario, evidence());

  assert.equal(result.coverageStatus, "FULL");
  assert.deepEqual(result.warnings, []);
});

test("keeps partial symbol coverage as an explicit warning", () => {
  const partial = evidence();
  partial.reviewDiff.fullyMappedFiles = 0;
  partial.reviewDiff.partiallyMappedFiles = 1;
  partial.reviewDiff.files[0].coverageStatus = "PARTIAL";
  partial.reviewDiff.files[0].baseChangedLines = [{ startLine: 3, endLine: 3 }];
  partial.latestReviewDiff = { ...partial.reviewDiff };

  const result = verifyScenarioEvidence(scenario, partial);

  assert.equal(result.coverageStatus, "PARTIAL");
  assert.deepEqual(result.unmappedBaseLines, [{ startLine: 3, endLine: 3 }]);
  assert.deepEqual(result.unmappedTargetLines, []);
  assert.match(result.warnings[0], /^BASE Diff lines/);
});

test("rejects revision drift, skipped coverage and gold ranges outside the Diff", () => {
  const drifted = evidence();
  drifted.reviewDiff.targetRevision = "c".repeat(40);
  assert.throws(() => verifyScenarioEvidence(scenario, drifted), /target revision drifted/);

  const skipped = evidence();
  skipped.reviewDiff.files[0].coverageStatus = "SKIPPED";
  assert.throws(() => verifyScenarioEvidence(scenario, skipped), /coverage was skipped/);

  const outside = evidence();
  outside.reviewDiff.files[0].changedLines = [{ startLine: 50, endLine: 55 }];
  assert.throws(() => verifyScenarioEvidence(scenario, outside), /gold range does not overlap/);
});

test("rejects gold ranges without target revision symbol evidence", () => {
  const missingTargetEvidence = evidence();
  missingTargetEvidence.reviewDiff.files[0].mappedSymbols = [
    { chunkId: "402", revisionSide: "TARGET", startLine: 20, endLine: 30 },
    { revisionSide: "BASE", startLine: 9, endLine: 13 },
  ];

  assert.throws(
    () => verifyScenarioEvidence(scenario, missingTargetEvidence),
    /gold range has no mapped target Diff line evidence/,
  );
});

test("requires one gold line to be both changed and mapped on the target revision", () => {
  const disjointEvidence = evidence();
  disjointEvidence.reviewDiff.fullyMappedFiles = 0;
  disjointEvidence.reviewDiff.partiallyMappedFiles = 1;
  disjointEvidence.reviewDiff.files[0].coverageStatus = "PARTIAL";
  disjointEvidence.reviewDiff.files[0].changedLines = [{ startLine: 10, endLine: 10 }];
  disjointEvidence.reviewDiff.files[0].mappedSymbols = [
    { chunkId: "402", revisionSide: "TARGET", startLine: 20, endLine: 20 },
    { revisionSide: "BASE", startLine: 9, endLine: 13 },
  ];
  const wideGoldScenario = {
    ...scenario,
    defects: [{ startLine: 10, endLine: 20 }],
  };

  assert.throws(
    () => verifyScenarioEvidence(wideGoldScenario, disjointEvidence),
    /gold range has no mapped target Diff line evidence/,
  );
});

test("rejects target evidence without a persisted chunk ID and valid line range", () => {
  const invalidTargetEvidence = evidence();
  invalidTargetEvidence.reviewDiff.files[0].mappedSymbols = [
    { revisionSide: "TARGET", startLine: 9, endLine: 13 },
    { chunkId: "402", revisionSide: "TARGET", startLine: 14, endLine: 13 },
    { revisionSide: "BASE", startLine: 9, endLine: 13 },
  ];

  assert.throws(
    () => verifyScenarioEvidence(scenario, invalidTargetEvidence),
    /has no target revision symbol evidence/,
  );
});

test("does not count unpersisted target symbols toward full line coverage", () => {
  const unpersistedCoverage = evidence();
  unpersistedCoverage.reviewDiff.files[0].mappedSymbols = [
    { chunkId: "401", revisionSide: "TARGET", startLine: 10, endLine: 12 },
    { revisionSide: "TARGET", startLine: 9, endLine: 13 },
    { revisionSide: "BASE", startLine: 9, endLine: 13 },
  ];

  assert.throws(
    () => verifyScenarioEvidence(scenario, unpersistedCoverage),
    /coverage status does not match its line evidence/,
  );
});

test("rejects coverage status and aggregate counter mismatches", () => {
  const inconsistent = evidence();
  inconsistent.reviewDiff.fullyMappedFiles = 0;
  inconsistent.reviewDiff.partiallyMappedFiles = 1;

  assert.throws(
    () => verifyScenarioEvidence(scenario, inconsistent),
    /coverage status does not match its counters/,
  );
});

test("reports readable API errors without returning raw non-JSON bodies", async () => {
  const client = new DevMateClient(
    "http://localhost:8080",
    async () => new Response("proxy failure", { status: 502 }),
    100,
  );

  await assert.rejects(
    client.get("/api/health"),
    (error) => error instanceof VerificationError && /non-JSON response/.test(error.message),
  );
});

test("reuses the latest successful import while still creating and verifying a fresh Diff", async () => {
  const currentEvidence = evidence();
  const projectListing = {
    ...currentEvidence.project,
    name: scenario.projectName,
    sourceType: "GIT",
    sourceLocation: scenario.repositoryUrl,
    defaultBranch: scenario.repositoryBranch,
  };
  const postedPaths = [];
  const client = {
    listProjectsByName: async () => [projectListing],
    get: async (requestPath) => {
      if (requestPath.endsWith("/imports/latest")) {
        return currentEvidence.latestImportTask;
      }
      if (requestPath.endsWith("/review-diffs/latest")) {
        return currentEvidence.latestReviewDiff;
      }
      return currentEvidence.project;
    },
    post: async (requestPath) => {
      postedPaths.push(requestPath);
      assert.match(requestPath, /\/review-diffs$/);
      return currentEvidence.reviewDiff;
    },
  };

  const result = await verifyOneScenario(client, scenario, true);

  assert.equal(result.importTriggered, false);
  assert.deepEqual(postedPaths, ["/api/projects/101/review-diffs"]);
});

test("refuses reuse mode when its deterministic project does not exist", async () => {
  const client = {
    listProjectsByName: async () => [],
  };

  await assert.rejects(
    verifyOneScenario(client, scenario, true),
    /does not exist; run without --reuse-imports first/,
  );
});

test("does not create a Diff when the reused import evidence is invalid", async () => {
  const currentEvidence = evidence();
  currentEvidence.importTask.status = "FAILED";
  currentEvidence.latestImportTask.status = "FAILED";
  const projectListing = {
    ...currentEvidence.project,
    name: scenario.projectName,
    sourceType: "GIT",
    sourceLocation: scenario.repositoryUrl,
    defaultBranch: scenario.repositoryBranch,
  };
  const postedPaths = [];
  const client = {
    listProjectsByName: async () => [projectListing],
    get: async (requestPath) => (
      requestPath.endsWith("/imports/latest")
        ? currentEvidence.latestImportTask
        : currentEvidence.project
    ),
    post: async (requestPath) => {
      postedPaths.push(requestPath);
      return currentEvidence.reviewDiff;
    },
  };

  await assert.rejects(
    verifyOneScenario(client, scenario, true),
    /import did not succeed/,
  );
  assert.deepEqual(postedPaths, []);
});

test("parses retry and reuse options without changing the default API URL", () => {
  const options = parseArguments([
    "--scenario", "case-008",
    "--report", "target/retry.json",
    "--reuse-imports",
  ]);

  assert.equal(options.scenario, "case-008");
  assert.equal(options.reuseImports, true);
  assert.equal(options.baseUrl, "http://localhost:8080");
  assert.match(options.reportPath, /target\/retry\.json$/);
  assert.throws(() => parseArguments(["--scenario"]), /requires a value/);
  assert.throws(() => parseArguments(["--report"]), /--report requires a value/);
  assert.throws(() => parseArguments(["--base-url", "--help"]), /--base-url requires a value/);
});
