#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  DevMateClient,
  VerificationError,
  buildExpectedEvaluationCases,
  mergeScenarioDefinitions,
  selectExistingProject,
  verifyEvaluationCases,
} from "./verify-live-imports.mjs";

const SCRIPT_PATH = fileURLToPath(import.meta.url);
const FIXTURE_ROOT = fileURLToPath(new URL("./known-defects-v1/", import.meta.url));
const DEFAULT_REPORT_PATH = fileURLToPath(
  new URL("../../target/benchmark-results/known-defects-v1-review-ab.json", import.meta.url),
);
const DEFAULT_BASE_URL = "http://localhost:8080";
const EXPECTED_SCENARIO_COUNT = 8;
const SHA_256_PATTERN = /^[a-f0-9]{64}$/;
const POSITIVE_ID_PATTERN = /^[1-9][0-9]*$/;
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DEFAULT_REQUEST_TIMEOUT_MS = 1_200_000;
const DEFAULT_RECOVERY_TIMEOUT_MS = 1_200_000;
const DEFAULT_RECOVERY_DELAY_MS = 5_000;

function requireCondition(condition, message) {
  if (!condition) {
    throw new VerificationError(message);
  }
}

function requireText(value, label) {
  requireCondition(typeof value === "string" && value.length > 0, `${label} is required`);
  return value;
}

function requireStringId(value, label) {
  requireCondition(
    typeof value === "string" && POSITIVE_ID_PATTERN.test(value),
    `${label} must be a positive string ID`,
  );
  return value;
}

function requireCount(value, label) {
  requireCondition(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative integer`);
  return value;
}

function requireLatency(value, label) {
  requireCondition(Number.isSafeInteger(value) && value >= 0, `${label} must be a non-negative integer`);
  return value;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function normalizePath(value) {
  return value.split(path.sep).join("/");
}

async function listJavaFiles(rootDirectory, currentDirectory = rootDirectory) {
  const entries = await readdir(currentDirectory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const entryPath = path.join(currentDirectory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listJavaFiles(rootDirectory, entryPath));
    } else if (entry.isFile() && entry.name.endsWith(".java")) {
      files.push(normalizePath(path.relative(rootDirectory, entryPath)));
    }
  }
  return files.sort();
}

function parseJson(source, label) {
  try {
    return JSON.parse(source.toString("utf8"));
  } catch {
    throw new VerificationError(`${label} is not valid JSON`);
  }
}

export async function loadAbScenarioPlan(fixtureRoot = FIXTURE_ROOT) {
  const manifestPath = path.join(fixtureRoot, "manifest.json");
  const revisionsPath = path.join(fixtureRoot, "revisions.json");
  const [manifestSource, revisionsSource] = await Promise.all([
    readFile(manifestPath),
    readFile(revisionsPath),
  ]);
  const manifest = parseJson(manifestSource, "Review manifest");
  const revisions = parseJson(revisionsSource, "Review revisions");
  const candidatePathsByScenario = new Map();
  for (const scenario of manifest.scenarios ?? []) {
    const candidateRoot = path.join(fixtureRoot, "scenarios", scenario.scenarioKey, "candidate");
    candidatePathsByScenario.set(scenario.scenarioKey, await listJavaFiles(candidateRoot));
  }
  const scenarios = mergeScenarioDefinitions(manifest, revisions, candidatePathsByScenario);
  requireCondition(
    scenarios.length === EXPECTED_SCENARIO_COUNT,
    `Controlled A/B requires exactly ${EXPECTED_SCENARIO_COUNT} manifest scenarios`,
  );
  return {
    datasetVersion: manifest.datasetVersion,
    scenarios,
    manifestSha256: sha256(manifestSource),
    revisionsSha256: sha256(revisionsSource),
  };
}

function verifyProject(expected, listedProject, currentProject) {
  requireStringId(listedProject?.id, `${expected.scenarioKey} listed project ID`);
  requireStringId(currentProject?.id, `${expected.scenarioKey} current project ID`);
  requireCondition(
    listedProject.id === currentProject.id,
    `${expected.scenarioKey} project ID drifted during preflight`,
  );
  selectExistingProject([currentProject], expected);
  requireCondition(currentProject.status === "READY", `${expected.scenarioKey} project is not READY`);
  requireCondition(
    currentProject.currentRevision === expected.candidateRevision,
    `${expected.scenarioKey} project candidate revision drifted`,
  );
}

export function verifyLatestReviewDiff(expected, project, reviewDiff) {
  requireStringId(reviewDiff?.id, `${expected.scenarioKey} Diff ID`);
  requireStringId(reviewDiff?.projectId, `${expected.scenarioKey} Diff project ID`);
  requireCondition(reviewDiff.status === "SUCCEEDED", `${expected.scenarioKey} latest Diff did not succeed`);
  requireCondition(
    reviewDiff.projectId === project.id,
    `${expected.scenarioKey} latest Diff belongs to another project`,
  );
  requireCondition(
    reviewDiff.baseRevision === expected.baseRevision,
    `${expected.scenarioKey} latest Diff base revision drifted`,
  );
  requireCondition(
    reviewDiff.targetRevision === expected.candidateRevision,
    `${expected.scenarioKey} latest Diff target revision drifted`,
  );
  requireCondition(
    reviewDiff.targetRevision === project.currentRevision,
    `${expected.scenarioKey} latest Diff and project candidate revision differ`,
  );
}

export function verifyLatestStaticAnalysis(expected, project, reviewDiff, staticAnalysis) {
  requireStringId(staticAnalysis?.id, `${expected.scenarioKey} static analysis ID`);
  requireStringId(staticAnalysis?.projectId, `${expected.scenarioKey} static analysis project ID`);
  requireStringId(staticAnalysis?.reviewTaskId, `${expected.scenarioKey} static analysis Diff ID`);
  requireCondition(
    staticAnalysis.status === "SUCCEEDED",
    `${expected.scenarioKey} latest static analysis did not succeed`,
  );
  requireCondition(
    staticAnalysis.projectId === project.id,
    `${expected.scenarioKey} latest static analysis belongs to another project`,
  );
  requireCondition(
    staticAnalysis.reviewTaskId === reviewDiff.id,
    `${expected.scenarioKey} latest static analysis belongs to another Diff`,
  );
}

export async function preflightScenario(client, scenario) {
  const listedProjects = await client.listProjectsByName(scenario.projectName);
  const listedProject = selectExistingProject(listedProjects, scenario);
  requireCondition(listedProject, `${scenario.projectName} does not exist`);
  requireStringId(listedProject.id, `${scenario.scenarioKey} project ID`);

  const currentProject = await client.get(`/api/projects/${listedProject.id}`);
  verifyProject(scenario, listedProject, currentProject);
  const reviewDiff = await client.get(`/api/projects/${currentProject.id}/review-diffs/latest`);
  verifyLatestReviewDiff(scenario, currentProject, reviewDiff);

  const expectedCases = buildExpectedEvaluationCases(scenario, {
    projectId: currentProject.id,
    reviewTaskId: reviewDiff.id,
  });
  const savedCases = await client.listReviewEvaluationCases(
    currentProject.id,
    scenario.datasetVersion,
  );
  const verifiedCases = verifyEvaluationCases(expectedCases, savedCases, scenario.scenarioKey);

  const staticAnalysis = await client.get(
    `/api/projects/${currentProject.id}/static-analyses/latest`,
  );
  verifyLatestStaticAnalysis(scenario, currentProject, reviewDiff, staticAnalysis);
  return {
    scenario,
    project: currentProject,
    reviewDiff,
    staticAnalysis,
    evaluationCases: verifiedCases,
  };
}

function isAmbiguousRequestError(error) {
  if (typeof error?.ambiguous === "boolean") {
    return error.ambiguous;
  }
  if (error instanceof VerificationError && /\sfailed:/.test(error.message)) {
    return false;
  }
  return true;
}

function verifyAiIdentity(task, expected, modelBinding = null) {
  requireStringId(task?.id, `${expected.scenarioKey} ${expected.mode} AI task ID`);
  requireStringId(task?.projectId, `${expected.scenarioKey} ${expected.mode} AI project ID`);
  requireStringId(task?.reviewTaskId, `${expected.scenarioKey} ${expected.mode} AI Diff ID`);
  requireStringId(
    task?.staticAnalysisTaskId,
    `${expected.scenarioKey} ${expected.mode} AI static analysis ID`,
  );
  requireCondition(
    task.attemptKey === expected.attemptKey,
    `${expected.scenarioKey} ${expected.mode} AI attempt key drifted`,
  );
  requireCondition(
    task.projectId === expected.projectId,
    `${expected.scenarioKey} ${expected.mode} AI project drifted`,
  );
  requireCondition(
    task.reviewTaskId === expected.reviewTaskId,
    `${expected.scenarioKey} ${expected.mode} AI Diff drifted`,
  );
  requireCondition(
    task.staticAnalysisTaskId === expected.staticAnalysisTaskId,
    `${expected.scenarioKey} ${expected.mode} AI static analysis drifted`,
  );
  requireCondition(
    task.revision === expected.revision,
    `${expected.scenarioKey} ${expected.mode} AI revision drifted`,
  );
  requireCondition(
    task.executionMode === expected.mode,
    `${expected.scenarioKey} ${expected.mode} AI execution mode drifted`,
  );
  requireText(task.provider, `${expected.scenarioKey} ${expected.mode} AI provider`);
  requireText(task.modelName, `${expected.scenarioKey} ${expected.mode} AI model`);
  if (modelBinding) {
    requireCondition(
      task.provider === modelBinding.provider,
      `${expected.scenarioKey} ${expected.mode} AI provider drifted`,
    );
    requireCondition(
      task.modelName === modelBinding.modelName,
      `${expected.scenarioKey} ${expected.mode} AI model drifted`,
    );
  }
}

function verifySuccessfulAiTask(task, expected, modelBinding = null) {
  verifyAiIdentity(task, expected, modelBinding);
  requireCondition(task.status === "SUCCEEDED", `${expected.scenarioKey} ${expected.mode} AI task failed`);
  requireText(task.promptVersion, `${expected.scenarioKey} ${expected.mode} prompt version`);
  requireText(
    task.retrievalConfigVersion,
    `${expected.scenarioKey} ${expected.mode} retrieval config version`,
  );
  requireText(task.retrievalMode, `${expected.scenarioKey} ${expected.mode} retrieval mode`);
  const promptTokens = requireCount(
    task.promptTokens,
    `${expected.scenarioKey} ${expected.mode} prompt tokens`,
  );
  const completionTokens = requireCount(
    task.completionTokens,
    `${expected.scenarioKey} ${expected.mode} completion tokens`,
  );
  const totalTokens = requireCount(
    task.totalTokens,
    `${expected.scenarioKey} ${expected.mode} total tokens`,
  );
  requireCondition(
    promptTokens + completionTokens === totalTokens,
    `${expected.scenarioKey} ${expected.mode} token counters differ`,
  );
  requireLatency(task.latencyMs, `${expected.scenarioKey} ${expected.mode} latency`);
  requireCount(task.contextChunks, `${expected.scenarioKey} ${expected.mode} context chunks`);
  requireCount(task.findingCount, `${expected.scenarioKey} ${expected.mode} finding count`);
  requireCount(task.rejectedFindings, `${expected.scenarioKey} ${expected.mode} rejected findings`);
  requireCondition(Array.isArray(task.toolCalls), `${expected.scenarioKey} ${expected.mode} tool calls differ`);
  return task;
}

async function defaultSleeper(milliseconds) {
  await new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function recoverAiTaskByAttempt(client, expected, modelBinding, options) {
  const delayMs = options.recoveryDelayMs ?? DEFAULT_RECOVERY_DELAY_MS;
  const recoveryTimeoutMs = options.recoveryTimeoutMs ?? DEFAULT_RECOVERY_TIMEOUT_MS;
  const attempts = options.recoveryReadAttempts
    ?? Math.max(1, Math.floor(recoveryTimeoutMs / delayMs) + 1);
  const sleeper = options.sleeper ?? defaultSleeper;
  let lastError = new VerificationError(`${expected.scenarioKey} ${expected.mode} recovery found no task`);
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const recovered = await client.get(
        `/api/projects/${expected.projectId}/ai-reviews/attempts/${expected.attemptKey}`,
      );
      verifyAiIdentity(recovered, expected, modelBinding);
      if (recovered.status === "SUCCEEDED") {
        return verifySuccessfulAiTask(recovered, expected, modelBinding);
      }
      requireCondition(
        recovered.status === "RUNNING",
        `${expected.scenarioKey} ${expected.mode} recovered AI task did not succeed`,
      );
      lastError = new VerificationError(
        `${expected.scenarioKey} ${expected.mode} recovered AI task is still running`,
      );
    } catch (error) {
      lastError = error instanceof Error ? error : new VerificationError(String(error));
    }
    if (attempt < attempts) {
      await sleeper(delayMs);
    }
  }
  throw new VerificationError(
    `${expected.scenarioKey} ${expected.mode} response was ambiguous and exact recovery failed: ${lastError.message}`,
  );
}

async function createAiReview(client, expected, modelBinding, counters, dependencies) {
  const requestPath = expected.mode === "FIXED"
    ? `/api/projects/${expected.projectId}/ai-reviews`
    : `/api/projects/${expected.projectId}/ai-reviews/agent`;
  const attemptKeyFactory = dependencies.attemptKeyFactory ?? randomUUID;
  const attemptKey = attemptKeyFactory();
  requireCondition(
    typeof attemptKey === "string" && UUID_V4_PATTERN.test(attemptKey),
    `${expected.scenarioKey} ${expected.mode} attempt key is not a lowercase UUID v4`,
  );
  const expectedAttempt = { ...expected, attemptKey };
  const body = {
    reviewTaskId: expected.reviewTaskId,
    revision: expected.revision,
    attemptKey,
  };
  counters.aiPostsAttempted += 1;
  let task;
  try {
    task = await client.post(requestPath, body);
  } catch (error) {
    if (!isAmbiguousRequestError(error)) {
      throw error;
    }
    const recovered = await recoverAiTaskByAttempt(
      client,
      expectedAttempt,
      modelBinding,
      dependencies,
    );
    counters.aiResponsesRecovered += 1;
    return recovered;
  }
  return verifySuccessfulAiTask(task, expectedAttempt, modelBinding);
}

function parseMetric(value, label) {
  const number = typeof value === "string" ? Number(value) : value;
  requireCondition(
    typeof number === "number" && Number.isFinite(number) && number >= 0 && number <= 1,
    `${label} must be between zero and one`,
  );
  return number;
}

export function verifyEvaluationSnapshot(expected, aiTask, evaluation) {
  requireStringId(evaluation?.id, `${expected.scenarioKey} ${expected.mode} evaluation ID`);
  requireStringId(evaluation?.projectId, `${expected.scenarioKey} ${expected.mode} evaluation project ID`);
  requireStringId(evaluation?.reviewTaskId, `${expected.scenarioKey} ${expected.mode} evaluation Diff ID`);
  requireStringId(evaluation?.aiReviewTaskId, `${expected.scenarioKey} ${expected.mode} evaluated AI task ID`);
  const exactFields = [
    ["projectId", expected.projectId],
    ["reviewTaskId", expected.reviewTaskId],
    ["aiReviewTaskId", aiTask.id],
    ["datasetVersion", expected.datasetVersion],
    ["executionMode", expected.mode],
    ["revision", expected.revision],
    ["modelName", aiTask.modelName],
    ["promptVersion", aiTask.promptVersion],
    ["retrievalConfigVersion", aiTask.retrievalConfigVersion],
  ];
  for (const [field, value] of exactFields) {
    requireCondition(
      evaluation[field] === value,
      `${expected.scenarioKey} ${expected.mode} evaluation ${field} drifted`,
    );
  }
  requireCondition(
    evaluation.status === "SUCCEEDED",
    `${expected.scenarioKey} ${expected.mode} evaluation did not succeed`,
  );
  requireCondition(
    typeof evaluation.datasetHash === "string" && SHA_256_PATTERN.test(evaluation.datasetHash),
    `${expected.scenarioKey} ${expected.mode} evaluation dataset hash is invalid`,
  );
  requireCondition(
    evaluation.partialMetrics === false,
    `${expected.scenarioKey} ${expected.mode} evaluation requires manual review`,
  );
  for (const field of [
    "expectedDefects",
    "predictedFindings",
    "truePositives",
    "falsePositives",
    "falseNegatives",
    "manualReviewCount",
    "totalTokens",
    "toolCallCount",
    "toolSuccessCount",
  ]) {
    requireCount(evaluation[field], `${expected.scenarioKey} ${expected.mode} evaluation ${field}`);
  }
  requireLatency(evaluation.latencyMs, `${expected.scenarioKey} ${expected.mode} evaluation latency`);
  requireCondition(
    evaluation.manualReviewCount === 0,
    `${expected.scenarioKey} ${expected.mode} evaluation contains manual review items`,
  );
  requireCondition(
    evaluation.expectedDefects === expected.expectedDefects,
    `${expected.scenarioKey} ${expected.mode} evaluation expected-defect count drifted`,
  );
  requireCondition(
    evaluation.expectedDefects === evaluation.truePositives + evaluation.falseNegatives,
    `${expected.scenarioKey} ${expected.mode} evaluation defect counters differ`,
  );
  requireCondition(
    evaluation.predictedFindings === evaluation.truePositives + evaluation.falsePositives,
    `${expected.scenarioKey} ${expected.mode} evaluation finding counters differ`,
  );
  requireCondition(
    evaluation.toolSuccessCount <= evaluation.toolCallCount,
    `${expected.scenarioKey} ${expected.mode} evaluation tool counters differ`,
  );
  requireCondition(
    evaluation.totalTokens === aiTask.totalTokens,
    `${expected.scenarioKey} ${expected.mode} evaluation token total drifted`,
  );
  requireCondition(
    evaluation.latencyMs === aiTask.latencyMs,
    `${expected.scenarioKey} ${expected.mode} evaluation latency drifted`,
  );
  requireCondition(
    evaluation.toolCallCount === aiTask.toolCalls.length,
    `${expected.scenarioKey} ${expected.mode} evaluation tool-call total drifted`,
  );
  const successfulToolCalls = aiTask.toolCalls.filter((call) => call.status === "SUCCEEDED").length;
  requireCondition(
    evaluation.toolSuccessCount === successfulToolCalls,
    `${expected.scenarioKey} ${expected.mode} evaluation successful-tool total drifted`,
  );
  const reportedMetrics = {
    precision: formatMetric(parseMetric(
      evaluation.precision,
      `${expected.scenarioKey} ${expected.mode} precision`,
    )),
    recall: formatMetric(parseMetric(
      evaluation.recall,
      `${expected.scenarioKey} ${expected.mode} recall`,
    )),
    f1: formatMetric(parseMetric(evaluation.f1, `${expected.scenarioKey} ${expected.mode} F1`)),
  };
  const calculatedMetrics = calculateMicroMetrics(
    evaluation.truePositives,
    evaluation.falsePositives,
    evaluation.falseNegatives,
  );
  requireCondition(
    JSON.stringify(reportedMetrics) === JSON.stringify(calculatedMetrics),
    `${expected.scenarioKey} ${expected.mode} evaluation metrics drifted`,
  );
  requireCondition(Array.isArray(evaluation.results), `${expected.scenarioKey} ${expected.mode} results differ`);
  return evaluation;
}

async function recoverEvaluation(client, expected, aiTask) {
  const query = new URLSearchParams({
    datasetVersion: expected.datasetVersion,
    reviewTaskId: expected.reviewTaskId,
  });
  const runs = await client.get(
    `/api/projects/${expected.projectId}/review-evaluation-runs?${query}`,
  );
  requireCondition(Array.isArray(runs), `${expected.scenarioKey} evaluation recovery is not a list`);
  const matches = runs.filter((run) => run.aiReviewTaskId === aiTask.id);
  requireCondition(
    matches.length === 1,
    `${expected.scenarioKey} ${expected.mode} evaluation response was ambiguous and exact recovery failed`,
  );
  return verifyEvaluationSnapshot(expected, aiTask, matches[0]);
}

async function evaluateAiReview(client, expected, aiTask, counters) {
  counters.evaluationPostsAttempted += 1;
  let evaluation;
  try {
    evaluation = await client.post(
      `/api/projects/${expected.projectId}/review-evaluation-runs`,
      {
        datasetVersion: expected.datasetVersion,
        aiReviewTaskId: aiTask.id,
      },
    );
  } catch (error) {
    if (!isAmbiguousRequestError(error)) {
      throw error;
    }
    const recovered = await recoverEvaluation(client, expected, aiTask);
    counters.evaluationResponsesRecovered += 1;
    return recovered;
  }
  return verifyEvaluationSnapshot(expected, aiTask, evaluation);
}

function comparePair(expected, fixedTask, fixedEvaluation, agentTask, agentEvaluation) {
  for (const field of ["projectId", "reviewTaskId", "revision", "provider", "modelName"]) {
    requireCondition(
      fixedTask[field] === agentTask[field],
      `${expected.scenarioKey} FIXED/AGENT ${field} drifted`,
    );
  }
  requireCondition(
    fixedEvaluation.datasetHash === agentEvaluation.datasetHash,
    `${expected.scenarioKey} FIXED/AGENT datasetHash drifted`,
  );
  return {
    provider: fixedTask.provider,
    modelName: fixedTask.modelName,
    datasetHash: fixedEvaluation.datasetHash,
  };
}

function aiSnapshot(task) {
  return {
    id: task.id,
    projectId: task.projectId,
    reviewTaskId: task.reviewTaskId,
    staticAnalysisTaskId: task.staticAnalysisTaskId,
    attemptKey: task.attemptKey,
    revision: task.revision,
    provider: task.provider,
    modelName: task.modelName,
    promptVersion: task.promptVersion,
    retrievalConfigVersion: task.retrievalConfigVersion,
    retrievalMode: task.retrievalMode,
    executionMode: task.executionMode,
    status: task.status,
    contextChunks: task.contextChunks,
    findingCount: task.findingCount,
    rejectedFindings: task.rejectedFindings,
    promptTokens: task.promptTokens,
    completionTokens: task.completionTokens,
    totalTokens: task.totalTokens,
    latencyMs: task.latencyMs,
  };
}

function evaluationSnapshot(evaluation) {
  return {
    id: evaluation.id,
    projectId: evaluation.projectId,
    reviewTaskId: evaluation.reviewTaskId,
    aiReviewTaskId: evaluation.aiReviewTaskId,
    datasetVersion: evaluation.datasetVersion,
    datasetHash: evaluation.datasetHash,
    executionMode: evaluation.executionMode,
    revision: evaluation.revision,
    modelName: evaluation.modelName,
    promptVersion: evaluation.promptVersion,
    retrievalConfigVersion: evaluation.retrievalConfigVersion,
    status: evaluation.status,
    expectedDefects: evaluation.expectedDefects,
    predictedFindings: evaluation.predictedFindings,
    truePositives: evaluation.truePositives,
    falsePositives: evaluation.falsePositives,
    falseNegatives: evaluation.falseNegatives,
    manualReviewCount: evaluation.manualReviewCount,
    partialMetrics: evaluation.partialMetrics,
    precision: formatMetric(parseMetric(evaluation.precision, "precision")),
    recall: formatMetric(parseMetric(evaluation.recall, "recall")),
    f1: formatMetric(parseMetric(evaluation.f1, "F1")),
    totalTokens: evaluation.totalTokens,
    latencyMs: evaluation.latencyMs,
    toolCallCount: evaluation.toolCallCount,
    toolSuccessCount: evaluation.toolSuccessCount,
  };
}

function preflightSnapshot(value) {
  return {
    projectId: value.project.id,
    reviewTaskId: value.reviewDiff.id,
    staticAnalysisTaskId: value.staticAnalysis.id,
    baseRevision: value.reviewDiff.baseRevision,
    revision: value.reviewDiff.targetRevision,
    goldCaseCount: value.evaluationCases.length,
  };
}

function sanitizeErrorMessage(error) {
  const original = error instanceof Error ? error.message : String(error);
  let code = "UNEXPECTED_ERROR";
  if (/preflight/i.test(original)) code = "PREFLIGHT_FAILED";
  else if (/timed out|timeout/i.test(original)) code = "REQUEST_TIMEOUT";
  else if (/ambiguous|recovery/i.test(original)) code = "AMBIGUOUS_RESPONSE";
  else if (/drifted|differs|mismatch/i.test(original)) code = "IDENTITY_DRIFT";
  else if (/does not exist|not found|暂无|不存在/i.test(original)) code = "RESOURCE_MISSING";
  else if (/quota|rate.?limit/i.test(original)) code = "PROVIDER_LIMIT";
  else if (/requires|must|invalid|unknown argument/i.test(original)) code = "CONTRACT_INVALID";
  return `${code}: inspect the phase and stored task identifiers; raw error text is intentionally omitted`;
}

async function runScenarioPair(client, preflight, state, counters, dependencies, scenarioResult) {
  const common = {
    scenarioKey: preflight.scenario.scenarioKey,
    projectId: preflight.project.id,
    reviewTaskId: preflight.reviewDiff.id,
    staticAnalysisTaskId: preflight.staticAnalysis.id,
    revision: preflight.scenario.candidateRevision,
    datasetVersion: preflight.scenario.datasetVersion,
    expectedDefects: preflight.evaluationCases.filter((value) => (
      value.expectationType === "DEFECT"
    )).length,
  };

  const fixedExpected = { ...common, mode: "FIXED" };
  const fixedTask = await createAiReview(
    client,
    fixedExpected,
    state.modelBinding,
    counters,
    dependencies,
  );
  if (state.modelBinding === null) {
    state.modelBinding = { provider: fixedTask.provider, modelName: fixedTask.modelName };
  }
  scenarioResult.fixed = { ai: aiSnapshot(fixedTask), evaluation: null };
  const fixedEvaluation = await evaluateAiReview(client, fixedExpected, fixedTask, counters);
  scenarioResult.fixed.evaluation = evaluationSnapshot(fixedEvaluation);

  const agentExpected = { ...common, mode: "AGENT" };
  const agentTask = await createAiReview(
    client,
    agentExpected,
    state.modelBinding,
    counters,
    dependencies,
  );
  scenarioResult.agent = { ai: aiSnapshot(agentTask), evaluation: null };
  const agentEvaluation = await evaluateAiReview(client, agentExpected, agentTask, counters);
  scenarioResult.agent.evaluation = evaluationSnapshot(agentEvaluation);
  const comparison = comparePair(
    common,
    fixedTask,
    fixedEvaluation,
    agentTask,
    agentEvaluation,
  );
  return {
    comparison,
  };
}

function roundRatioScaled(numerator, denominator) {
  if (denominator === 0) {
    return 1_000_000n;
  }
  const scaledNumerator = BigInt(numerator) * 1_000_000n;
  const divisor = BigInt(denominator);
  return (scaledNumerator * 2n + divisor) / (divisor * 2n);
}

function formatScaledMetric(value) {
  const whole = value / 1_000_000n;
  const fraction = (value % 1_000_000n).toString().padStart(6, "0");
  return `${whole}.${fraction}`;
}

function formatMetric(value) {
  return value.toFixed(6);
}

export function calculateMicroMetrics(truePositives, falsePositives, falseNegatives) {
  requireCount(truePositives, "true positives");
  requireCount(falsePositives, "false positives");
  requireCount(falseNegatives, "false negatives");
  const precision = roundRatioScaled(truePositives, truePositives + falsePositives);
  const recall = roundRatioScaled(truePositives, truePositives + falseNegatives);
  let f1 = 0n;
  if (precision !== 0n && recall !== 0n) {
    const numerator = 2n * precision * recall;
    const denominator = precision + recall;
    f1 = (numerator * 2n + denominator) / (denominator * 2n);
  }
  return {
    precision: formatScaledMetric(precision),
    recall: formatScaledMetric(recall),
    f1: formatScaledMetric(f1),
  };
}

function latencySummary(values) {
  if (values.length === 0) {
    return { count: 0, totalMs: 0, minMs: null, maxMs: null, averageMs: null };
  }
  const totalMs = values.reduce((total, value) => total + value, 0);
  return {
    count: values.length,
    totalMs,
    minMs: Math.min(...values),
    maxMs: Math.max(...values),
    averageMs: Number((totalMs / values.length).toFixed(2)),
  };
}

export function aggregateModeResults(results, modeKey) {
  const completed = results.filter((result) => result.status === "COMPLETED");
  const snapshots = completed.map((result) => result[modeKey]);
  const aiSnapshots = results
    .map((result) => result[modeKey]?.ai)
    .filter((snapshot) => snapshot !== undefined);
  const evaluationSnapshots = results
    .map((result) => result[modeKey]?.evaluation)
    .filter((snapshot) => snapshot != null);
  const counts = {
    expectedDefects: 0,
    predictedFindings: 0,
    truePositives: 0,
    falsePositives: 0,
    falseNegatives: 0,
  };
  const tokens = { prompt: 0, completion: 0, total: 0 };
  const tools = { calls: 0, succeeded: 0, failed: 0 };
  for (const snapshot of snapshots) {
    const evaluation = snapshot.evaluation;
    counts.expectedDefects += evaluation.expectedDefects;
    counts.predictedFindings += evaluation.predictedFindings;
    counts.truePositives += evaluation.truePositives;
    counts.falsePositives += evaluation.falsePositives;
    counts.falseNegatives += evaluation.falseNegatives;
  }
  for (const snapshot of aiSnapshots) {
    tokens.prompt += snapshot.promptTokens;
    tokens.completion += snapshot.completionTokens;
    tokens.total += snapshot.totalTokens;
  }
  for (const evaluation of evaluationSnapshots) {
    tools.calls += evaluation.toolCallCount;
    tools.succeeded += evaluation.toolSuccessCount;
  }
  tools.failed = tools.calls - tools.succeeded;
  return {
    runs: snapshots.length,
    aiRuns: aiSnapshots.length,
    evaluatedRuns: evaluationSnapshots.length,
    counts,
    microMetrics: snapshots.length === 0
      ? null
      : calculateMicroMetrics(
        counts.truePositives,
        counts.falsePositives,
        counts.falseNegatives,
      ),
    tokens,
    latency: latencySummary(aiSnapshots.map((snapshot) => snapshot.latencyMs)),
    tools,
  };
}

function buildReport(plan, options, scenarioResults, counters, startedAt, finishedAt, runFailed) {
  const completed = scenarioResults.filter((result) => result.status === "COMPLETED").length;
  const failed = scenarioResults.filter((result) => (
    result.status === "PREFLIGHT_FAILED" || result.status === "FAILED"
  )).length;
  const fullScope = options.scenario === null;
  const fullDatasetCompleted = fullScope
    && scenarioResults.length === EXPECTED_SCENARIO_COUNT
    && completed === EXPECTED_SCENARIO_COUNT
    && failed === 0;
  const notRun = scenarioResults.filter((result) => result.status === "NOT_RUN").length;
  requireCondition(
    scenarioResults.length === completed + failed + notRun,
    "A/B report contains a non-terminal scenario status",
  );
  return {
    schemaVersion: "review-ab-v1",
    datasetVersion: plan.datasetVersion,
    scope: fullScope ? "FULL" : "CANARY",
    selectedScenario: options.scenario,
    fixtureDigests: {
      algorithm: "SHA-256",
      manifest: plan.manifestSha256,
      revisions: plan.revisionsSha256,
    },
    startedAt,
    finishedAt,
    status: failed === 0 && !runFailed ? "SUCCEEDED" : "FAILED",
    fullDatasetCompleted,
    counts: {
      plannedScenarios: scenarioResults.length,
      preflightPassed: scenarioResults.filter((result) => result.preflight !== undefined).length,
      pairsCompleted: completed,
      failed,
      notRun,
      aiPostsAttempted: counters.aiPostsAttempted,
      evaluationPostsAttempted: counters.evaluationPostsAttempted,
      aiResponsesRecovered: counters.aiResponsesRecovered,
      evaluationResponsesRecovered: counters.evaluationResponsesRecovered,
    },
    aggregates: {
      fixed: aggregateModeResults(scenarioResults, "fixed"),
      agent: aggregateModeResults(scenarioResults, "agent"),
    },
    scenarios: scenarioResults,
  };
}

async function writeReport(reportPath, report) {
  await mkdir(path.dirname(reportPath), { recursive: true });
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

function validatePlan(plan, options) {
  requireText(plan?.datasetVersion, "A/B dataset version");
  requireCondition(Array.isArray(plan?.scenarios), "A/B scenarios must be an array");
  requireCondition(
    typeof plan.manifestSha256 === "string" && SHA_256_PATTERN.test(plan.manifestSha256),
    "Manifest SHA-256 is invalid",
  );
  requireCondition(
    typeof plan.revisionsSha256 === "string" && SHA_256_PATTERN.test(plan.revisionsSha256),
    "Revisions SHA-256 is invalid",
  );
  if (options.scenario === null) {
    requireCondition(
      plan.scenarios.length === EXPECTED_SCENARIO_COUNT,
      `Full A/B report requires all ${EXPECTED_SCENARIO_COUNT} scenarios`,
    );
  }
}

export async function runReviewAb(options, dependencies = {}) {
  const plan = dependencies.plan ?? await loadAbScenarioPlan();
  validatePlan(plan, options);
  const client = dependencies.client ?? new DevMateClient(
    options.baseUrl,
    globalThis.fetch,
    options.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS,
  );
  const reportWriter = dependencies.reportWriter ?? writeReport;
  const now = dependencies.now ?? (() => new Date().toISOString());
  const runtimeOptions = { ...options, ...dependencies };
  const selected = options.scenario
    ? plan.scenarios.filter((scenario) => (
      scenario.scenarioKey === options.scenario || scenario.repositoryBranch === options.scenario
    ))
    : plan.scenarios;
  requireCondition(selected.length === 1 || options.scenario === null, `Unknown scenario: ${options.scenario}`);

  const startedAt = now();
  const counters = {
    aiPostsAttempted: 0,
    evaluationPostsAttempted: 0,
    aiResponsesRecovered: 0,
    evaluationResponsesRecovered: 0,
  };
  const preflights = new Map();
  const scenarioResults = selected.map((scenario) => ({
    scenarioKey: scenario.scenarioKey,
    status: "PREFLIGHT_PENDING",
  }));
  let runError = null;

  try {
    const health = await client.get("/api/health");
    requireCondition(health?.status === "UP", "DevMate health endpoint is not UP");
    for (let index = 0; index < selected.length; index += 1) {
      const scenario = selected[index];
      const result = scenarioResults[index];
      try {
        const preflight = await preflightScenario(client, scenario);
        preflights.set(scenario.scenarioKey, preflight);
        result.status = "PREFLIGHT_PASSED";
        result.preflight = preflightSnapshot(preflight);
      } catch (error) {
        result.status = "PREFLIGHT_FAILED";
        result.failure = { phase: "PREFLIGHT", message: sanitizeErrorMessage(error) };
      }
    }
    const preflightFailures = scenarioResults.filter((result) => result.status === "PREFLIGHT_FAILED");
    if (preflightFailures.length > 0) {
      for (const result of scenarioResults) {
        if (result.status === "PREFLIGHT_PASSED") {
          result.status = "NOT_RUN";
        }
      }
    }
    requireCondition(
      preflightFailures.length === 0,
      `${preflightFailures.length} A/B preflight scenarios failed`,
    );

    const state = { modelBinding: null };
    for (let index = 0; index < selected.length; index += 1) {
      const scenario = selected[index];
      const result = scenarioResults[index];
      try {
        const pair = await runScenarioPair(
          client,
          preflights.get(scenario.scenarioKey),
          state,
          counters,
          runtimeOptions,
          result,
        );
        result.status = "COMPLETED";
        result.comparison = pair.comparison;
      } catch (error) {
        result.status = "FAILED";
        result.failure = { phase: "EXECUTION", message: sanitizeErrorMessage(error) };
        for (let later = index + 1; later < scenarioResults.length; later += 1) {
          scenarioResults[later].status = "NOT_RUN";
        }
        throw error;
      }
    }
  } catch (error) {
    runError = error instanceof Error ? error : new VerificationError(String(error));
  }

  if (runError && scenarioResults.every((result) => result.status === "PREFLIGHT_PENDING")) {
    scenarioResults[0].status = "FAILED";
    scenarioResults[0].failure = { phase: "HEALTH", message: sanitizeErrorMessage(runError) };
  }
  for (const result of scenarioResults) {
    if (result.status === "PREFLIGHT_PENDING") {
      result.status = "NOT_RUN";
    }
  }

  const report = buildReport(
    plan,
    options,
    scenarioResults,
    counters,
    startedAt,
    now(),
    runError !== null,
  );
  await reportWriter(options.reportPath, report);
  if (runError) {
    const failure = new VerificationError(sanitizeErrorMessage(runError));
    failure.report = report;
    throw failure;
  }
  requireCondition(
    options.scenario !== null || report.fullDatasetCompleted,
    `Full A/B report requires all ${EXPECTED_SCENARIO_COUNT} completed pairs`,
  );
  return report;
}

function validateBaseUrl(value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new VerificationError("--base-url must be a valid URL");
  }
  requireCondition(["http:", "https:"].includes(parsed.protocol), "--base-url must use HTTP or HTTPS");
  requireCondition(!parsed.username && !parsed.password, "--base-url must not contain credentials");
  return value;
}

export function parseAbArguments(args) {
  const options = {
    baseUrl: process.env.DEVMATE_BASE_URL ?? DEFAULT_BASE_URL,
    reportPath: DEFAULT_REPORT_PATH,
    scenario: null,
    requestTimeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
    recoveryTimeoutMs: DEFAULT_RECOVERY_TIMEOUT_MS,
    recoveryDelayMs: DEFAULT_RECOVERY_DELAY_MS,
    help: false,
  };
  const readValue = (index, option) => {
    const value = args[index + 1];
    requireCondition(value && !value.startsWith("--"), `${option} requires a value`);
    return value;
  };
  const readSeconds = (index, option, minimum, maximum) => {
    const value = Number(readValue(index, option));
    requireCondition(
      Number.isSafeInteger(value) && value >= minimum && value <= maximum,
      `${option} must be an integer between ${minimum} and ${maximum} seconds`,
    );
    return value * 1_000;
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--base-url") {
      options.baseUrl = readValue(index, argument);
      index += 1;
    } else if (argument === "--report") {
      options.reportPath = path.resolve(readValue(index, argument));
      index += 1;
    } else if (argument === "--scenario") {
      options.scenario = readValue(index, argument);
      index += 1;
    } else if (argument === "--request-timeout-seconds") {
      options.requestTimeoutMs = readSeconds(index, argument, 60, 3_600);
      index += 1;
    } else if (argument === "--recovery-timeout-seconds") {
      options.recoveryTimeoutMs = readSeconds(index, argument, 60, 3_600);
      index += 1;
    } else if (argument === "--recovery-interval-seconds") {
      options.recoveryDelayMs = readSeconds(index, argument, 1, 60);
      index += 1;
    } else if (argument === "--help") {
      options.help = true;
    } else {
      throw new VerificationError(`Unknown argument: ${argument}`);
    }
  }
  options.baseUrl = validateBaseUrl(options.baseUrl);
  return options;
}

function printHelp() {
  console.log(`Usage: node benchmarks/review-fixtures/run-review-ab.mjs [options]

Options:
  --base-url URL   DevMate API URL (default: ${DEFAULT_BASE_URL})
  --report PATH    Sanitized JSON report path (default: target/benchmark-results/...)
  --scenario KEY   Run one scenario key or case-NNN canary; never marks a full report complete
  --request-timeout-seconds N   Per-request timeout, 60-3600 (default: 1200)
  --recovery-timeout-seconds N  Ambiguous-response recovery window, 60-3600 (default: 1200)
  --recovery-interval-seconds N Poll interval, 1-60 (default: 5)
  --help           Show this help

The full run preflights all eight projects before making any model call, then executes
FIXED -> evaluate -> AGENT -> evaluate sequentially for each scenario. A paid POST is
never retried after an ambiguous response; only an exact attempt-key readback is accepted.`);
}

async function main() {
  const options = parseAbArguments(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const report = await runReviewAb(options);
  console.log(`Report: ${options.reportPath}`);
  console.log(report.fullDatasetCompleted ? "Full A/B completed" : "Canary completed");
}

if (process.argv[1] && path.resolve(process.argv[1]) === SCRIPT_PATH) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
