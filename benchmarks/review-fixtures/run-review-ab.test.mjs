import assert from "node:assert/strict";
import test from "node:test";

import {
  aggregateModeResults,
  calculateMicroMetrics,
  loadAbScenarioPlan,
  parseAbArguments,
  runReviewAb,
} from "./run-review-ab.mjs";
import { buildExpectedEvaluationCases } from "./verify-live-imports.mjs";

const ACTUAL_PLAN = await loadAbScenarioPlan();

function savedCases(scenario, projectId, reviewTaskId, sequence) {
  return buildExpectedEvaluationCases(scenario, { projectId, reviewTaskId }).map((expected, index) => {
    const { scenarioKey: ignoredScenarioKey, ...persisted } = expected;
    return { id: String(5_000 + sequence * 10 + index), ...persisted };
  });
}

function createBundle(scenario, index) {
  const projectId = String(1_000 + index);
  const reviewTaskId = String(2_000 + index);
  const staticAnalysisTaskId = String(3_000 + index);
  return {
    index,
    scenario,
    project: {
      id: projectId,
      name: scenario.projectName,
      sourceType: "GIT",
      sourceLocation: scenario.repositoryUrl,
      defaultBranch: scenario.repositoryBranch,
      status: "READY",
      currentRevision: scenario.candidateRevision,
    },
    reviewDiff: {
      id: reviewTaskId,
      projectId,
      status: "SUCCEEDED",
      baseRevision: scenario.baseRevision,
      targetRevision: scenario.candidateRevision,
    },
    staticAnalysis: {
      id: staticAnalysisTaskId,
      projectId,
      reviewTaskId,
      status: "SUCCEEDED",
      findings: [{ evidence: "source must never enter the A/B report" }],
    },
    evaluationCases: savedCases(scenario, projectId, reviewTaskId, index),
    latestAi: null,
    evaluations: [],
  };
}

function makeAiTask(bundle, mode, sequence, attemptKey) {
  const agent = mode === "AGENT";
  const toolCalls = agent
    ? [
      {
        id: String(8_000 + sequence * 10),
        status: "SUCCEEDED",
        argumentsSummary: "token=TEST_PRIVATE_VALUE source-snippet",
      },
      {
        id: String(8_001 + sequence * 10),
        status: "FAILED",
        resultSummary: "private source must stay out",
      },
    ]
    : [];
  const promptTokens = agent ? 200 + sequence : 100 + sequence;
  const completionTokens = agent ? 30 : 20;
  return {
    id: String(4_000 + sequence * 10 + (agent ? 2 : 1)),
    projectId: bundle.project.id,
    reviewTaskId: bundle.reviewDiff.id,
    staticAnalysisTaskId: bundle.staticAnalysis.id,
    invocationId: String(7_000 + sequence * 10 + (agent ? 2 : 1)),
    attemptKey,
    revision: bundle.scenario.candidateRevision,
    provider: "DASHSCOPE",
    modelName: "qwen-plus",
    promptVersion: agent ? "review-agent-v1" : "ai-review-v1",
    executionMode: mode,
    retrievalConfigVersion: agent ? "review-agent-retrieval-v1" : "retrieval-v1",
    retrievalMode: agent ? "AGENT" : "HYBRID",
    status: "SUCCEEDED",
    contextChunks: agent ? 5 : 4,
    findingCount: bundle.scenario.expectationType === "DEFECT" ? 1 : 0,
    rejectedFindings: 0,
    promptTokens,
    completionTokens,
    totalTokens: promptTokens + completionTokens,
    latencyMs: (agent ? 2_000 : 1_000) + sequence,
    findings: [{ evidence: "private-source-body" }],
    toolCalls,
  };
}

function makeEvaluation(bundle, aiTask, sequence) {
  const expectedDefects = bundle.scenario.expectationType === "DEFECT" ? 1 : 0;
  const truePositives = expectedDefects;
  const metrics = calculateMicroMetrics(truePositives, 0, 0);
  const toolSuccessCount = aiTask.toolCalls.filter((call) => call.status === "SUCCEEDED").length;
  return {
    id: String(6_000 + sequence * 10 + (aiTask.executionMode === "AGENT" ? 2 : 1)),
    projectId: bundle.project.id,
    reviewTaskId: bundle.reviewDiff.id,
    aiReviewTaskId: aiTask.id,
    datasetVersion: bundle.scenario.datasetVersion,
    datasetHash: "a".repeat(64),
    executionMode: aiTask.executionMode,
    revision: aiTask.revision,
    modelName: aiTask.modelName,
    promptVersion: aiTask.promptVersion,
    retrievalConfigVersion: aiTask.retrievalConfigVersion,
    status: "SUCCEEDED",
    expectedDefects,
    predictedFindings: truePositives,
    truePositives,
    falsePositives: 0,
    falseNegatives: 0,
    manualReviewCount: 0,
    partialMetrics: false,
    ...metrics,
    totalTokens: aiTask.totalTokens,
    latencyMs: aiTask.latencyMs,
    toolCallCount: aiTask.toolCalls.length,
    toolSuccessCount,
    results: [],
  };
}

function createFakeClient(plan = ACTUAL_PLAN, hooks = {}) {
  const bundles = plan.scenarios.map(createBundle);
  const byName = new Map(bundles.map((bundle) => [bundle.project.name, bundle]));
  const byProjectId = new Map(bundles.map((bundle) => [bundle.project.id, bundle]));
  const aiById = new Map();
  const aiByAttempt = new Map();
  const calls = [];
  let aiSequence = 0;
  let evaluationSequence = 0;

  const client = {
    listProjectsByName: async (name) => {
      calls.push({ kind: "PROJECT_LIST", name });
      const bundle = byName.get(name);
      return bundle ? [bundle.project] : [];
    },
    listReviewEvaluationCases: async (projectId, datasetVersion) => {
      calls.push({ kind: "GOLD_LIST", projectId: String(projectId), datasetVersion });
      const bundle = byProjectId.get(String(projectId));
      assert.ok(bundle, `Unexpected gold-case project ${projectId}`);
      return bundle.evaluationCases;
    },
    get: async (requestPath) => {
      calls.push({ kind: "GET", requestPath });
      if (requestPath === "/api/health") {
        return { status: "UP" };
      }
      const projectId = requestPath.match(/^\/api\/projects\/([0-9]+)/)?.[1];
      const bundle = byProjectId.get(projectId);
      assert.ok(bundle, `Unexpected GET ${requestPath}`);
      if (requestPath.endsWith("/review-diffs/latest")) {
        return bundle.reviewDiff;
      }
      if (requestPath.endsWith("/static-analyses/latest")) {
        return bundle.staticAnalysis;
      }
      if (requestPath.endsWith("/ai-reviews/latest")) {
        if (hooks.latestAi) {
          return hooks.latestAi({ bundle, requestPath });
        }
        if (!bundle.latestAi) {
          const error = new Error("project has no AI review task");
          error.notFound = true;
          throw error;
        }
        return bundle.latestAi;
      }
      const attemptKey = requestPath.match(/\/ai-reviews\/attempts\/([0-9a-f-]+)$/)?.[1];
      if (attemptKey) {
        const task = aiByAttempt.get(attemptKey);
        if (!task) {
          const error = new Error("attempt task does not exist");
          error.notFound = true;
          throw error;
        }
        return task;
      }
      if (requestPath.includes("/review-evaluation-runs?")) {
        return bundle.evaluations;
      }
      if (requestPath === `/api/projects/${projectId}`) {
        return bundle.project;
      }
      assert.fail(`Unexpected GET ${requestPath}`);
    },
    post: async (requestPath, body) => {
      calls.push({ kind: "POST", requestPath, body });
      const projectId = requestPath.match(/^\/api\/projects\/([0-9]+)/)?.[1];
      const bundle = byProjectId.get(projectId);
      assert.ok(bundle, `Unexpected POST ${requestPath}`);
      if (requestPath.endsWith("/review-evaluation-runs")) {
        const aiTask = aiById.get(String(body.aiReviewTaskId));
        assert.ok(aiTask, `Unknown AI task ${body.aiReviewTaskId}`);
        const evaluation = makeEvaluation(bundle, aiTask, evaluationSequence);
        evaluationSequence += 1;
        const succeed = () => {
          bundle.evaluations.unshift(evaluation);
          return evaluation;
        };
        if (hooks.evaluationPost) {
          return hooks.evaluationPost({ bundle, aiTask, evaluation, body, succeed });
        }
        return succeed();
      }
      const mode = requestPath.endsWith("/agent") ? "AGENT" : "FIXED";
      assert.match(body.attemptKey, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
      const aiTask = makeAiTask(bundle, mode, aiSequence, body.attemptKey);
      aiSequence += 1;
      const succeed = () => {
        bundle.latestAi = aiTask;
        aiById.set(aiTask.id, aiTask);
        aiByAttempt.set(aiTask.attemptKey, aiTask);
        return aiTask;
      };
      if (hooks.aiPost) {
        return hooks.aiPost({ bundle, mode, aiTask, body, succeed });
      }
      return succeed();
    },
  };
  return { client, calls, bundles };
}

function options(overrides = {}) {
  return {
    baseUrl: "http://localhost:8080",
    reportPath: "ignored.json",
    scenario: null,
    ...overrides,
  };
}

function dependencies(plan, harness, sink) {
  let tick = 0;
  let attemptSequence = 0;
  return {
    plan,
    client: harness.client,
    reportWriter: async (reportPath, report) => {
      assert.equal(reportPath, "ignored.json");
      sink.report = report;
    },
    now: () => `2026-08-06T00:00:${String(tick++).padStart(2, "0")}.000Z`,
    sleeper: async () => {},
    recoveryReadAttempts: 2,
    recoveryDelayMs: 0,
    attemptKeyFactory: () => {
      const suffix = String(++attemptSequence).padStart(12, "0");
      return `00000000-0000-4000-8000-${suffix}`;
    },
  };
}

test("loads all eight manifest/revision scenarios with stable SHA-256 digests", async () => {
  const plan = await loadAbScenarioPlan();

  assert.equal(plan.scenarios.length, 8);
  assert.equal(new Set(plan.scenarios.map((scenario) => scenario.scenarioKey)).size, 8);
  assert.match(plan.manifestSha256, /^[a-f0-9]{64}$/);
  assert.match(plan.revisionsSha256, /^[a-f0-9]{64}$/);
  assert.notEqual(plan.manifestSha256, plan.revisionsSha256);
});

test("runs every successful pair in FIXED/evaluate/AGENT/evaluate order after the full preflight", async () => {
  const harness = createFakeClient();
  const sink = {};
  const report = await runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink));
  const posts = harness.calls.filter((call) => call.kind === "POST");

  assert.equal(posts.length, 32);
  assert.equal(
    harness.calls.slice(0, harness.calls.indexOf(posts[0])).filter((call) => call.kind === "GOLD_LIST").length,
    8,
  );
  for (let index = 0; index < 8; index += 1) {
    const bundle = harness.bundles[index];
    const group = posts.slice(index * 4, index * 4 + 4);
    assert.deepEqual(
      group.map((call) => call.requestPath),
      [
        `/api/projects/${bundle.project.id}/ai-reviews`,
        `/api/projects/${bundle.project.id}/review-evaluation-runs`,
        `/api/projects/${bundle.project.id}/ai-reviews/agent`,
        `/api/projects/${bundle.project.id}/review-evaluation-runs`,
      ],
    );
    assert.deepEqual(group[0].body, {
      reviewTaskId: bundle.reviewDiff.id,
      revision: bundle.scenario.candidateRevision,
      attemptKey: `00000000-0000-4000-8000-${String(index * 2 + 1).padStart(12, "0")}`,
    });
    assert.deepEqual(group[2].body, {
      reviewTaskId: bundle.reviewDiff.id,
      revision: bundle.scenario.candidateRevision,
      attemptKey: `00000000-0000-4000-8000-${String(index * 2 + 2).padStart(12, "0")}`,
    });
  }
  assert.equal(report.fullDatasetCompleted, true);
  assert.equal(report.counts.pairsCompleted, 8);
  assert.equal(report.counts.aiPostsAttempted, 16);
  assert.equal(report.counts.evaluationPostsAttempted, 16);
  assert.equal(report.scenarios[0].fixed.ai.promptVersion, "ai-review-v1");
  assert.equal(report.scenarios[0].agent.ai.promptVersion, "review-agent-v1");
  assert.notEqual(
    report.scenarios[0].fixed.ai.retrievalConfigVersion,
    report.scenarios[0].agent.ai.retrievalConfigVersion,
  );
  assert.deepEqual(sink.report, report);
});

test("makes zero model POSTs when a later Diff, gold case, or static preflight drifts", async () => {
  for (const drift of ["DIFF", "GOLD", "STATIC"]) {
    const harness = createFakeClient();
    const sink = {};
    const last = harness.bundles.at(-1);
    if (drift === "DIFF") {
      last.reviewDiff.targetRevision = "f".repeat(40);
    } else if (drift === "GOLD") {
      last.evaluationCases[0].targetRevision = "e".repeat(40);
    } else {
      last.staticAnalysis.reviewTaskId = "999999";
    }

    await assert.rejects(
      runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
      /PREFLIGHT_FAILED/,
    );
    assert.equal(harness.calls.filter((call) => call.kind === "POST").length, 0);
    assert.equal(sink.report.status, "FAILED");
    assert.equal(sink.report.counts.aiPostsAttempted, 0);
    assert.equal(sink.report.scenarios.at(-1).status, "PREFLIGHT_FAILED");
    assert.equal(
      sink.report.counts.plannedScenarios,
      sink.report.counts.pairsCompleted + sink.report.counts.failed + sink.report.counts.notRun,
    );
  }
});

test("rejects numeric IDs during preflight before any paid request", async () => {
  const harness = createFakeClient();
  const sink = {};
  harness.bundles[3].reviewDiff.id = 2003;

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /PREFLIGHT_FAILED/,
  );
  assert.equal(harness.calls.filter((call) => call.kind === "POST").length, 0);
  assert.match(sink.report.scenarios[3].failure.message, /CONTRACT_INVALID/);
});

test("rejects model and dataset drift while allowing prompt/retrieval version differences", async (t) => {
  await t.test("model drift", async () => {
    const harness = createFakeClient(ACTUAL_PLAN, {
      aiPost: ({ mode, aiTask, succeed }) => {
        if (mode === "AGENT") {
          aiTask.modelName = "different-model";
        }
        return succeed();
      },
    });
    const sink = {};

    await assert.rejects(
      runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
      /IDENTITY_DRIFT/,
    );
    assert.equal(sink.report.counts.aiPostsAttempted, 2);
    assert.equal(sink.report.counts.evaluationPostsAttempted, 1);
    assert.equal(sink.report.scenarios[1].status, "NOT_RUN");
  });

  await t.test("dataset hash drift", async () => {
    const harness = createFakeClient(ACTUAL_PLAN, {
      evaluationPost: ({ aiTask, evaluation, succeed }) => {
        if (aiTask.executionMode === "AGENT") {
          evaluation.datasetHash = "b".repeat(64);
        }
        return succeed();
      },
    });
    const sink = {};

    await assert.rejects(
      runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
      /IDENTITY_DRIFT/,
    );
    assert.equal(sink.report.counts.aiPostsAttempted, 2);
    assert.equal(sink.report.counts.evaluationPostsAttempted, 2);
    assert.equal(sink.report.scenarios[0].fixed.evaluation.datasetHash, "a".repeat(64));
    assert.equal(sink.report.scenarios[0].agent.evaluation.datasetHash, "b".repeat(64));
  });
});

test("recovers an exact committed AI task after response loss without retrying the paid POST", async () => {
  let lost = false;
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: ({ bundle, mode, succeed }) => {
      const task = succeed();
      if (!lost && bundle.index === 0 && mode === "FIXED") {
        lost = true;
        const error = new Error("connection reset after commit");
        error.ambiguous = true;
        throw error;
      }
      return task;
    },
  });
  const sink = {};
  const report = await runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink));
  const firstFixedPosts = harness.calls.filter((call) => (
    call.kind === "POST" && call.requestPath === "/api/projects/1000/ai-reviews"
  ));

  assert.equal(firstFixedPosts.length, 1);
  assert.equal(report.counts.aiPostsAttempted, 16);
  assert.equal(report.counts.aiResponsesRecovered, 1);
  assert.equal(report.scenarios[0].fixed.ai.executionMode, "FIXED");
});

test("uses the attempt key instead of a concurrent latest task during response recovery", async () => {
  let lost = false;
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: ({ bundle, mode, succeed }) => {
      const task = succeed();
      if (!lost && bundle.index === 0 && mode === "FIXED") {
        lost = true;
        bundle.latestAi = makeAiTask(
          bundle,
          "FIXED",
          99,
          "00000000-0000-4000-8000-999999999999",
        );
        const error = new Error("connection reset while another operator submitted a task");
        error.ambiguous = true;
        throw error;
      }
      return task;
    },
  });
  const sink = {};

  const report = await runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink));

  assert.equal(report.counts.aiResponsesRecovered, 1);
  assert.equal(report.scenarios[0].fixed.ai.id, "4001");
  assert.notEqual(report.scenarios[0].fixed.ai.id, harness.bundles[0].latestAi.id);
});

test("recovers an exact evaluation after its POST response is lost", async () => {
  let lost = false;
  const harness = createFakeClient(ACTUAL_PLAN, {
    evaluationPost: ({ bundle, succeed }) => {
      const evaluation = succeed();
      if (!lost && bundle.index === 0) {
        lost = true;
        const error = new Error("connection reset after evaluation commit");
        error.ambiguous = true;
        throw error;
      }
      return evaluation;
    },
  });
  const sink = {};

  const report = await runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink));

  assert.equal(report.counts.evaluationPostsAttempted, 16);
  assert.equal(report.counts.evaluationResponsesRecovered, 1);
  assert.equal(report.scenarios[0].fixed.evaluation.aiReviewTaskId, "4001");
});

test("rejects partial metrics before starting the AGENT half of a pair", async () => {
  const harness = createFakeClient(ACTUAL_PLAN, {
    evaluationPost: ({ evaluation, succeed }) => {
      evaluation.partialMetrics = true;
      evaluation.manualReviewCount = 1;
      return succeed();
    },
  });
  const sink = {};

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /CONTRACT_INVALID/,
  );
  assert.equal(sink.report.counts.aiPostsAttempted, 1);
  assert.equal(sink.report.counts.evaluationPostsAttempted, 1);
  assert.equal(sink.report.scenarios[1].status, "NOT_RUN");
});

test("does not accept a drifted latest task as response-loss recovery evidence", async () => {
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: ({ bundle, mode, aiTask, succeed }) => {
      if (bundle.index === 0 && mode === "FIXED") {
        aiTask.reviewTaskId = "999999";
        succeed();
        const error = new Error("connection reset");
        error.ambiguous = true;
        throw error;
      }
      return succeed();
    },
  });
  const sink = {};

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /AMBIGUOUS_RESPONSE/,
  );
  assert.equal(sink.report.counts.aiPostsAttempted, 1);
  assert.equal(harness.calls.filter((call) => (
    call.kind === "POST" && call.requestPath.endsWith("/ai-reviews")
  )).length, 1);
  assert.equal(sink.report.scenarios[1].status, "NOT_RUN");
});

test("does not recover an unrelated latest task when the request never reached the service", async () => {
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: ({ bundle, mode, succeed }) => {
      if (bundle.index === 0 && mode === "FIXED") {
        const error = new Error("request may not have reached the service");
        error.ambiguous = true;
        throw error;
      }
      return succeed();
    },
  });
  harness.bundles[0].latestAi = makeAiTask(
    harness.bundles[0],
    "FIXED",
    99,
    "00000000-0000-4000-8000-999999999999",
  );
  const previousId = harness.bundles[0].latestAi.id;
  const sink = {};

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /AMBIGUOUS_RESPONSE/,
  );
  assert.equal(harness.bundles[0].latestAi.id, previousId);
  assert.equal(sink.report.counts.aiPostsAttempted, 1);
  assert.equal(harness.calls.filter((call) => call.kind === "POST").length, 1);
});

test("stops before all later scenarios after an unambiguous pair failure", async () => {
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: ({ bundle, mode, succeed }) => {
      if (bundle.index === 1 && mode === "AGENT") {
        const error = new Error("model quota rejected request");
        error.ambiguous = false;
        throw error;
      }
      return succeed();
    },
  });
  const sink = {};

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /PROVIDER_LIMIT/,
  );
  assert.equal(sink.report.scenarios[0].status, "COMPLETED");
  assert.equal(sink.report.scenarios[1].status, "FAILED");
  assert.ok(sink.report.scenarios.slice(2).every((result) => result.status === "NOT_RUN"));
  assert.equal(sink.report.counts.aiPostsAttempted, 4);
  assert.equal(sink.report.counts.evaluationPostsAttempted, 3);
});

test("calculates micro metrics from summed TP/FP/FN and aggregates cost statistics", () => {
  const result = (tp, fp, fn, prompt, completion, latency, calls, succeeded) => ({
    status: "COMPLETED",
    fixed: {
      ai: {
        promptTokens: prompt,
        completionTokens: completion,
        totalTokens: prompt + completion,
        latencyMs: latency,
      },
      evaluation: {
        expectedDefects: tp + fn,
        predictedFindings: tp + fp,
        truePositives: tp,
        falsePositives: fp,
        falseNegatives: fn,
        toolCallCount: calls,
        toolSuccessCount: succeeded,
      },
    },
  });
  const aggregate = aggregateModeResults([
    result(1, 0, 0, 10, 2, 100, 1, 1),
    result(0, 3, 1, 20, 4, 300, 4, 3),
  ], "fixed");

  assert.deepEqual(calculateMicroMetrics(1, 3, 1), {
    precision: "0.250000",
    recall: "0.500000",
    f1: "0.333333",
  });
  assert.deepEqual(calculateMicroMetrics(0, 0, 0), {
    precision: "1.000000",
    recall: "1.000000",
    f1: "1.000000",
  });
  assert.deepEqual(aggregate.microMetrics, {
    precision: "0.250000",
    recall: "0.500000",
    f1: "0.333333",
  });
  assert.deepEqual(aggregate.tokens, { prompt: 30, completion: 6, total: 36 });
  assert.deepEqual(aggregate.latency, {
    count: 2,
    totalMs: 400,
    minMs: 100,
    maxMs: 300,
    averageMs: 200,
  });
  assert.deepEqual(aggregate.tools, { calls: 5, succeeded: 4, failed: 1 });
});

test("keeps the report free of API locations, raw source, tool payloads and secrets", async () => {
  const harness = createFakeClient();
  const sink = {};
  await runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink));
  const reportJson = JSON.stringify(sink.report);

  assert.doesNotMatch(reportJson, /localhost:8080/);
  assert.doesNotMatch(reportJson, /devmate-review-benchmark/);
  assert.doesNotMatch(reportJson, /TEST_PRIVATE_VALUE/);
  assert.doesNotMatch(reportJson, /private-source-body|source-snippet|private source must stay out/);
  assert.doesNotMatch(reportJson, /"findings"|"toolCalls"|"argumentsSummary"|"resultSummary"/);
  assert.match(reportJson, /"manifest":"[a-f0-9]{64}"/);
  assert.equal(sink.report.scenarios.every((result) => typeof result.preflight.projectId === "string"), true);
  assert.equal(sink.report.scenarios.every((result) => typeof result.fixed.ai.id === "string"), true);
});

test("never writes raw failure text, source, prompt or opaque bearer credentials to reports", async () => {
  const harness = createFakeClient(ACTUAL_PLAN, {
    aiPost: () => {
      const error = new Error(
        "Authorization: Bearer opaque-private-value prompt=private-prompt source=private-source-line",
      );
      error.ambiguous = false;
      throw error;
    },
  });
  const sink = {};

  await assert.rejects(
    runReviewAb(options(), dependencies(ACTUAL_PLAN, harness, sink)),
    /UNEXPECTED_ERROR/,
  );
  const reportJson = JSON.stringify(sink.report);
  assert.doesNotMatch(reportJson, /opaque-private-value|private-prompt|private-source-line|Bearer/);
  assert.match(reportJson, /UNEXPECTED_ERROR/);
});

test("marks a one-scenario canary successful without claiming full report completion", async () => {
  const harness = createFakeClient();
  const sink = {};
  const report = await runReviewAb(
    options({ scenario: "case-001" }),
    dependencies(ACTUAL_PLAN, harness, sink),
  );

  assert.equal(report.scope, "CANARY");
  assert.equal(report.fullDatasetCompleted, false);
  assert.equal(report.counts.plannedScenarios, 1);
  assert.equal(report.counts.pairsCompleted, 1);
  assert.equal(report.status, "SUCCEEDED");
});

test("refuses to label a seven-scenario plan as a full A/B run", async () => {
  const incompletePlan = {
    ...ACTUAL_PLAN,
    scenarios: ACTUAL_PLAN.scenarios.slice(0, 7),
  };

  await assert.rejects(
    runReviewAb(options(), {
      plan: incompletePlan,
      client: {
        get: async () => assert.fail("An incomplete full plan must fail before API access"),
      },
    }),
    /requires all 8 scenarios/,
  );
});

test("parses only controlled CLI options and refuses credentials in the API URL", () => {
  const parsed = parseAbArguments([
    "--base-url", "https://devmate.example.test",
    "--report", "target/ab.json",
    "--scenario", "case-001",
    "--request-timeout-seconds", "900",
    "--recovery-timeout-seconds", "600",
    "--recovery-interval-seconds", "10",
  ]);

  assert.equal(parsed.baseUrl, "https://devmate.example.test");
  assert.equal(parsed.scenario, "case-001");
  assert.equal(parsed.requestTimeoutMs, 900_000);
  assert.equal(parsed.recoveryTimeoutMs, 600_000);
  assert.equal(parsed.recoveryDelayMs, 10_000);
  assert.match(parsed.reportPath, /target\/ab\.json$/);
  assert.throws(() => parseAbArguments(["--scenario"]), /requires a value/);
  assert.throws(() => parseAbArguments(["--unknown"]), /Unknown argument/);
  assert.throws(
    () => parseAbArguments(["--base-url", "https://user:password@example.test"]),
    /must not contain credentials/,
  );
  assert.throws(
    () => parseAbArguments(["--request-timeout-seconds", "59"]),
    /between 60 and 3600 seconds/,
  );
});
