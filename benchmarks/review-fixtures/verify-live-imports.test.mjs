import assert from "node:assert/strict";
import test from "node:test";

import {
  DevMateClient,
  VerificationError,
  buildExpectedEvaluationCases,
  mergeScenarioDefinitions,
  parseArguments,
  planEvaluationCaseSync,
  runLiveVerification,
  selectExistingProject,
  syncEvaluationCaseBatch,
  toEvaluationCaseRequest,
  verifyEvaluationCases,
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
  name: "Sample defect",
  expectationType: "DEFECT",
  rationale: "Scenario rationale",
  defects: [{
    caseKey: "sample-defect",
    category: "CONCURRENCY",
    filePath: "src/main/java/example/Sample.java",
    startLine: 10,
    endLine: 12,
    rationale: "Defect rationale",
  }],
};

const cleanScenario = {
  ...scenario,
  scenarioKey: "clean-sample",
  repositoryBranch: "case-002",
  projectName: "benchmark-known-defects-v1-case-002",
  name: "Clean sample",
  expectationType: "CLEAN",
  rationale: "No defect remains after the batch query change",
  defects: [],
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

function savedEvaluationCase(expected, id = "501", overrides = {}) {
  const { scenarioKey: ignoredScenarioKey, ...persisted } = expected;
  return { id, ...persisted, ...overrides };
}

function liveEvidence(fixtureScenario, sequence, reviewOverrides = {}) {
  const projectId = String(100 + sequence);
  const importTaskId = String(200 + sequence);
  const reviewTaskId = String(300 + sequence);
  const project = {
    id: projectId,
    name: fixtureScenario.projectName,
    sourceType: "GIT",
    sourceLocation: fixtureScenario.repositoryUrl,
    defaultBranch: fixtureScenario.repositoryBranch,
    status: "READY",
    currentRevision: fixtureScenario.candidateRevision,
  };
  const importTask = {
    id: importTaskId,
    projectId,
    status: "SUCCEEDED",
    revision: fixtureScenario.candidateRevision,
    totalFiles: 1,
    processedFiles: 1,
    failedFiles: 0,
  };
  const reviewDiff = {
    id: reviewTaskId,
    projectId,
    status: "SUCCEEDED",
    baseRevision: fixtureScenario.baseRevision,
    targetRevision: fixtureScenario.candidateRevision,
    changedFiles: 1,
    fullyMappedFiles: 1,
    partiallyMappedFiles: 0,
    skippedFiles: 0,
    files: [{
      oldPath: fixtureScenario.expectedFilePath,
      newPath: fixtureScenario.expectedFilePath,
      changeType: "MODIFY",
      coverageStatus: "FULL",
      baseChangedLines: [{ startLine: 9, endLine: 13 }],
      changedLines: [{ startLine: 9, endLine: 13 }],
      mappedSymbols: [
        { chunkId: String(400 + sequence), revisionSide: "TARGET", startLine: 9, endLine: 13 },
        { revisionSide: "BASE", startLine: 9, endLine: 13 },
      ],
    }],
    ...reviewOverrides,
  };
  return {
    project,
    importTask,
    reviewDiff,
  };
}

function reusableLiveClient(bundles, evaluationHandlers = {}) {
  const byName = new Map(bundles.map((value) => [value.project.name, value]));
  const byProjectId = new Map(bundles.map((value) => [value.project.id, value]));
  const calls = { evaluationLists: 0, evaluationPosts: [] };
  const client = {
    listProjectsByName: async (name) => {
      const bundle = byName.get(name);
      return bundle ? [bundle.project] : [];
    },
    get: async (requestPath) => {
      if (requestPath === "/api/health") {
        return { status: "UP" };
      }
      const projectId = requestPath.match(/\/api\/projects\/(\d+)/)?.[1];
      const bundle = byProjectId.get(projectId);
      assert.ok(bundle, `Unexpected project request: ${requestPath}`);
      if (requestPath.endsWith("/imports/latest")) {
        return bundle.importTask;
      }
      if (requestPath.endsWith("/review-diffs/latest")) {
        return bundle.reviewDiff;
      }
      return bundle.project;
    },
    listReviewEvaluationCases: async (...args) => {
      calls.evaluationLists += 1;
      return evaluationHandlers.list?.(...args) ?? [];
    },
    post: async (requestPath, body) => {
      assert.match(requestPath, /\/review-evaluation-cases$/);
      calls.evaluationPosts.push({ requestPath, body });
      if (evaluationHandlers.post) {
        return evaluationHandlers.post(requestPath, body);
      }
      assert.fail(`Unexpected POST: ${requestPath}`);
    },
  };
  return { client, calls };
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

test("builds exact DEFECT and CLEAN evaluation case requests", () => {
  const defect = buildExpectedEvaluationCases(scenario, {
    projectId: "101",
    reviewTaskId: "301",
  });
  const clean = buildExpectedEvaluationCases(cleanScenario, {
    projectId: "102",
    reviewTaskId: "302",
  });

  assert.equal(defect.length, 1);
  assert.deepEqual(toEvaluationCaseRequest(defect[0]), {
    reviewTaskId: "301",
    datasetVersion: "known-defects-v1",
    caseKey: "sample-defect",
    name: "Sample defect",
    expectationType: "DEFECT",
    rationale: "Defect rationale",
    category: "CONCURRENCY",
    filePath: scenario.expectedFilePath,
    startLine: 10,
    endLine: 12,
  });
  assert.equal(clean.length, 1);
  assert.deepEqual(toEvaluationCaseRequest(clean[0]), {
    reviewTaskId: "302",
    datasetVersion: "known-defects-v1",
    caseKey: "clean-sample",
    name: "Clean sample",
    expectationType: "CLEAN",
    rationale: cleanScenario.rationale,
  });
});

test("rejects manifest fields that the evaluation case DTO cannot accept", () => {
  const invalidScenarios = [
    { ...scenario, datasetVersion: "bad version" },
    { ...scenario, name: "n".repeat(201) },
    {
      ...scenario,
      defects: [{ ...scenario.defects[0], category: "UNKNOWN" }],
    },
    {
      ...scenario,
      defects: [{ ...scenario.defects[0], filePath: "p".repeat(1001) }],
    },
    {
      ...scenario,
      defects: [{ ...scenario.defects[0], rationale: "r".repeat(1001) }],
    },
  ];

  for (const invalid of invalidScenarios) {
    assert.throws(
      () => buildExpectedEvaluationCases(invalid, { projectId: "101", reviewTaskId: "301" }),
      VerificationError,
    );
  }
});

test("verifies saved evaluation fields and rejects semantic drift", () => {
  const expected = buildExpectedEvaluationCases(scenario, {
    projectId: "101",
    reviewTaskId: "301",
  });
  assert.equal(
    verifyEvaluationCases(expected, [savedEvaluationCase(expected[0])], scenario.scenarioKey).length,
    1,
  );

  const driftCases = [
    ["targetRevision", "c".repeat(40)],
    ["category", "SQL"],
    ["filePath", "src/main/java/example/sample.java"],
    ["startLine", 11],
    ["endLine", 13],
  ];
  for (const [field, value] of driftCases) {
    assert.throws(
      () => verifyEvaluationCases(
        expected,
        [savedEvaluationCase(expected[0], "501", { [field]: value })],
        scenario.scenarioKey,
      ),
      new RegExp(`${field} differs`),
    );
  }

  const clean = buildExpectedEvaluationCases(cleanScenario, {
    projectId: "102",
    reviewTaskId: "302",
  });
  assert.throws(
    () => verifyEvaluationCases(
      clean,
      [savedEvaluationCase(clean[0], "502", { category: "SQL" })],
      cleanScenario.scenarioKey,
    ),
    /category differs/,
  );
});

test("plans only missing cases and rejects extras or duplicate saved keys", () => {
  const expected = buildExpectedEvaluationCases(scenario, {
    projectId: "101",
    reviewTaskId: "301",
  });
  assert.equal(planEvaluationCaseSync(expected, [], scenario.scenarioKey).missingCases.length, 1);
  assert.equal(
    planEvaluationCaseSync(
      expected,
      [savedEvaluationCase(expected[0])],
      scenario.scenarioKey,
    ).missingCases.length,
    0,
  );
  assert.throws(
    () => planEvaluationCaseSync(
      expected,
      [{ ...savedEvaluationCase(expected[0]), caseKey: "unexpected" }],
      scenario.scenarioKey,
    ),
    /unexpected saved case/,
  );
  assert.throws(
    () => planEvaluationCaseSync(
      expected,
      [savedEvaluationCase(expected[0]), savedEvaluationCase(expected[0], "502")],
      scenario.scenarioKey,
    ),
    /duplicate saved case key/,
  );
});

test("creates missing evaluation cases and reuses the exact saved batch on rerun", async () => {
  const entries = [
    { scenario, result: { projectId: "101", reviewTaskId: "301" } },
    { scenario: cleanScenario, result: { projectId: "102", reviewTaskId: "302" } },
  ];
  const expected = entries.flatMap((entry) => buildExpectedEvaluationCases(entry.scenario, entry.result));
  const expectedByKey = new Map(expected.map((value) => [value.caseKey, value]));
  const savedByProject = new Map([["101", []], ["102", []]]);
  const posts = [];
  const client = {
    listReviewEvaluationCases: async (projectId, datasetVersion) => {
      assert.equal(datasetVersion, "known-defects-v1");
      return [...savedByProject.get(String(projectId))];
    },
    post: async (requestPath, body) => {
      posts.push({ requestPath, body });
      const value = savedEvaluationCase(expectedByKey.get(body.caseKey), String(500 + posts.length));
      const projectId = requestPath.match(/\/api\/projects\/(\d+)/)?.[1];
      savedByProject.get(projectId).push(value);
      return value;
    },
  };

  const first = await syncEvaluationCaseBatch(client, entries);
  const second = await syncEvaluationCaseBatch(client, entries);

  assert.equal(first.failure, null);
  assert.equal(first.outcomes.reduce((total, value) => total + value.createdCount, 0), 2);
  assert.equal(second.failure, null);
  assert.equal(second.outcomes.reduce((total, value) => total + value.reusedCount, 0), 2);
  assert.equal(posts.length, 2);
});

test("preflights old-Diff drift across the whole batch before writing any evaluation case", async () => {
  const firstExpected = buildExpectedEvaluationCases(scenario, {
    projectId: "101",
    reviewTaskId: "301",
  });
  const secondExpected = buildExpectedEvaluationCases(cleanScenario, {
    projectId: "102",
    reviewTaskId: "302",
  });
  let posts = 0;
  const client = {
    listReviewEvaluationCases: async (projectId) => (
      projectId === "101"
        ? []
        : [savedEvaluationCase(secondExpected[0], "502", { reviewTaskId: "999" })]
    ),
    post: async () => {
      posts += 1;
    },
  };

  const result = await syncEvaluationCaseBatch(client, [
    { scenario, result: { projectId: "101", reviewTaskId: "301" } },
    { scenario: cleanScenario, result: { projectId: "102", reviewTaskId: "302" } },
  ]);

  assert.equal(firstExpected.length, 1);
  assert.equal(result.failure.phase, "PREFLIGHT");
  assert.match(result.failure.error, /reviewTaskId differs/);
  assert.equal(posts, 0);
});

test("recovers a committed evaluation case when the POST response is lost", async () => {
  const entry = { scenario, result: { projectId: "101", reviewTaskId: "301" } };
  const expected = buildExpectedEvaluationCases(entry.scenario, entry.result)[0];
  const saved = [];
  let posts = 0;
  const client = {
    listReviewEvaluationCases: async () => [...saved],
    post: async () => {
      posts += 1;
      saved.push(savedEvaluationCase(expected));
      throw new VerificationError("response timed out");
    },
  };

  const result = await syncEvaluationCaseBatch(client, [entry]);

  assert.equal(result.failure, null);
  assert.equal(result.outcomes[0].recoveredCount, 1);
  assert.equal(result.outcomes[0].verifiedCount, 1);
  assert.equal(posts, 1);
});

test("preserves successful POST counters when final evaluation readback fails", async () => {
  const entry = { scenario, result: { projectId: "101", reviewTaskId: "301" } };
  let listCalls = 0;
  const client = {
    listReviewEvaluationCases: async () => {
      listCalls += 1;
      if (listCalls === 1) {
        return [];
      }
      throw new VerificationError("final readback failed");
    },
    post: async () => ({ id: "501" }),
  };

  const result = await syncEvaluationCaseBatch(client, [entry]);

  assert.equal(result.failure.phase, "APPLY");
  assert.equal(result.outcomes[0].status, "FAILED");
  assert.equal(result.outcomes[0].createdCount, 1);
  assert.equal(result.outcomes[0].verifiedCount, 0);
});

test("rejects a malformed later manifest case before writing the batch", async () => {
  const malformedScenario = {
    ...cleanScenario,
    expectationType: "DEFECT",
    defects: [{
      ...scenario.defects[0],
      caseKey: "bad key",
    }],
  };
  let posts = 0;
  const client = {
    listReviewEvaluationCases: async () => [],
    post: async () => {
      posts += 1;
    },
  };

  const result = await syncEvaluationCaseBatch(client, [
    { scenario, result: { projectId: "101", reviewTaskId: "301" } },
    { scenario: malformedScenario, result: { projectId: "102", reviewTaskId: "302" } },
  ]);

  assert.equal(result.failure.phase, "PREFLIGHT");
  assert.match(result.failure.error, /unsupported characters/);
  assert.equal(posts, 0);
});

test("does not write any gold case when a later Diff fails verification", async () => {
  const first = liveEvidence(scenario, 1);
  const second = liveEvidence(cleanScenario, 2, { status: "FAILED" });
  const { client, calls } = reusableLiveClient([first, second]);
  let report;

  await assert.rejects(
    runLiveVerification(
      {
        baseUrl: "http://localhost:8080",
        reportPath: "ignored.json",
        scenario: null,
        reuseImports: true,
        reuseDiffs: true,
        recordGoldCases: true,
      },
      {
        plan: {
          datasetVersion: scenario.datasetVersion,
          repositoryUrl: scenario.repositoryUrl,
          scenarios: [scenario, cleanScenario],
        },
        client,
        reportWriter: async (reportPath, value) => {
          assert.equal(reportPath, "ignored.json");
          report = value;
        },
      },
    ),
    /1 benchmark scenarios failed/,
  );

  assert.equal(calls.evaluationLists, 0);
  assert.equal(calls.evaluationPosts.length, 0);
  assert.deepEqual(report.summary, {
    total: 2,
    passed: 1,
    failed: 1,
    fullCoverage: 1,
    partialCoverage: 0,
    goldCasesExpected: 2,
    goldCasesVerified: 0,
    goldCasesCreated: 0,
    goldCasesRecovered: 0,
    goldCasesReused: 0,
  });
  assert.deepEqual(
    report.results.map((value) => value.goldCases.status),
    ["NOT_APPLIED", "NOT_APPLIED"],
  );
});

test("marks an evaluation apply failure explicitly in the written report", async () => {
  const bundle = liveEvidence(scenario, 1);
  const { client, calls } = reusableLiveClient([bundle], {
    list: async () => [],
    post: async () => {
      throw new VerificationError("evaluation write failed");
    },
  });
  let report;

  await assert.rejects(
    runLiveVerification(
      {
        baseUrl: "http://localhost:8080",
        reportPath: "ignored.json",
        scenario: null,
        reuseImports: true,
        reuseDiffs: true,
        recordGoldCases: true,
      },
      {
        plan: {
          datasetVersion: scenario.datasetVersion,
          repositoryUrl: scenario.repositoryUrl,
          scenarios: [scenario],
        },
        client,
        reportWriter: async (reportPath, value) => {
          assert.equal(reportPath, "ignored.json");
          report = value;
        },
      },
    ),
    /1 benchmark scenarios failed/,
  );

  assert.equal(calls.evaluationPosts.length, 1);
  assert.equal(report.summary.goldCasesExpected, 1);
  assert.equal(report.summary.goldCasesVerified, 0);
  assert.equal(report.results[0].status, "FAIL");
  assert.equal(report.results[0].failurePhase, "EVALUATION_CASE_APPLY");
  assert.deepEqual(
    {
      status: report.results[0].goldCases.status,
      failurePhase: report.results[0].goldCases.failurePhase,
      error: report.results[0].goldCases.error,
    },
    {
      status: "FAILED",
      failurePhase: "APPLY",
      error: "evaluation write failed",
    },
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

  const result = await verifyOneScenario(client, scenario, { reuseImports: true });

  assert.equal(result.importTriggered, false);
  assert.deepEqual(postedPaths, ["/api/projects/101/review-diffs"]);
});

test("refuses reuse mode when its deterministic project does not exist", async () => {
  const client = {
    listProjectsByName: async () => [],
  };

  await assert.rejects(
    verifyOneScenario(client, scenario, { reuseImports: true }),
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
    verifyOneScenario(client, scenario, { reuseImports: true }),
    /import did not succeed/,
  );
  assert.deepEqual(postedPaths, []);
});

test("reuses the latest verified Diff without creating another task", async () => {
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
    },
  };

  const result = await verifyOneScenario(client, scenario, {
    reuseImports: true,
    reuseDiffs: true,
  });

  assert.equal(result.importTriggered, false);
  assert.equal(result.diffTriggered, false);
  assert.equal(result.reviewTaskId, currentEvidence.latestReviewDiff.id);
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
  assert.throws(() => parseArguments(["--reuse-diffs"]), /requires --reuse-imports/);
  assert.throws(() => parseArguments(["--record-gold-cases"]), /requires --reuse-diffs/);
  assert.throws(
    () => parseArguments([
      "--reuse-imports",
      "--reuse-diffs",
      "--record-gold-cases",
      "--scenario",
      "case-001",
    ]),
    /cannot be combined with --scenario/,
  );

  const goldOptions = parseArguments([
    "--reuse-imports",
    "--reuse-diffs",
    "--record-gold-cases",
  ]);
  assert.equal(goldOptions.reuseImports, true);
  assert.equal(goldOptions.reuseDiffs, true);
  assert.equal(goldOptions.recordGoldCases, true);
});
