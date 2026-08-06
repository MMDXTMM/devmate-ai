#!/usr/bin/env node

import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_PATH = fileURLToPath(import.meta.url);
const FIXTURE_ROOT = fileURLToPath(new URL("./known-defects-v1/", import.meta.url));
const DEFAULT_REPORT_PATH = fileURLToPath(
  new URL("../../target/benchmark-results/known-defects-v1-import-diff.json", import.meta.url),
);
const DEFAULT_BASE_URL = "http://localhost:8080";
const DEFAULT_TIMEOUT_MS = 120_000;
const EVALUATION_KEY_PATTERN = /^[A-Za-z0-9._-]+$/;
const EVALUATION_CATEGORIES = new Set([
  "CONCURRENCY",
  "TRANSACTION",
  "CACHE",
  "MESSAGE",
  "SQL",
  "SECURITY",
  "ARCHITECTURE",
  "PERFORMANCE",
  "RELIABILITY",
]);

export class VerificationError extends Error {
  constructor(message) {
    super(message);
    this.name = "VerificationError";
  }
}

function requireCondition(condition, message) {
  if (!condition) {
    throw new VerificationError(message);
  }
}

function normalizePath(value) {
  return value.split(path.sep).join("/");
}

function rangesOverlap(left, right) {
  return left.startLine <= right.endLine && right.startLine <= left.endLine;
}

function rangesShareLine(...ranges) {
  return Math.max(...ranges.map((range) => range.startLine))
    <= Math.min(...ranges.map((range) => range.endLine));
}

function findUnmappedRanges(changedRanges, mappedSymbols, revisionSide) {
  const unmappedRanges = [];
  for (const changedRange of changedRanges) {
    let unmappedStart = null;
    for (let line = changedRange.startLine; line <= changedRange.endLine; line += 1) {
      const mapped = mappedSymbols.some((symbol) => (
        symbol.revisionSide === revisionSide
        && Number.isInteger(symbol.startLine)
        && Number.isInteger(symbol.endLine)
        && symbol.startLine <= line
        && symbol.endLine >= line
      ));
      if (!mapped && unmappedStart === null) {
        unmappedStart = line;
      } else if (mapped && unmappedStart !== null) {
        unmappedRanges.push({ startLine: unmappedStart, endLine: line - 1 });
        unmappedStart = null;
      }
    }
    if (unmappedStart !== null) {
      unmappedRanges.push({ startLine: unmappedStart, endLine: changedRange.endLine });
    }
  }
  return unmappedRanges;
}

async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, "utf8"));
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

export function mergeScenarioDefinitions(manifest, revisions, candidatePathsByScenario) {
  requireCondition(
    manifest.datasetVersion === revisions.datasetVersion,
    "Manifest and revision dataset versions differ",
  );
  requireCondition(
    manifest.repositoryUrl === revisions.repositoryUrl,
    "Manifest and revision repository URLs differ",
  );
  requireCondition(Array.isArray(manifest.scenarios), "Manifest scenarios must be an array");
  requireCondition(Array.isArray(revisions.scenarios), "Revision scenarios must be an array");

  const revisionByKey = new Map();
  for (const revision of revisions.scenarios) {
    requireCondition(!revisionByKey.has(revision.scenarioKey), `Duplicate revision scenario: ${revision.scenarioKey}`);
    revisionByKey.set(revision.scenarioKey, revision);
  }

  const seenBranches = new Set();
  const scenarios = manifest.scenarios.map((scenario) => {
    const revision = revisionByKey.get(scenario.scenarioKey);
    requireCondition(revision, `Missing revision scenario: ${scenario.scenarioKey}`);
    requireCondition(
      scenario.repositoryBranch === revision.repositoryBranch,
      `Branch mismatch for ${scenario.scenarioKey}`,
    );
    requireCondition(
      !seenBranches.has(scenario.repositoryBranch),
      `Duplicate repository branch: ${scenario.repositoryBranch}`,
    );
    seenBranches.add(scenario.repositoryBranch);

    const candidatePaths = candidatePathsByScenario.get(scenario.scenarioKey) ?? [];
    requireCondition(
      candidatePaths.length === 1,
      `${scenario.scenarioKey} must contain exactly one candidate Java file`,
    );
    const expectedFilePath = candidatePaths[0];
    for (const defect of scenario.defects ?? []) {
      requireCondition(
        defect.filePath === expectedFilePath,
        `${scenario.scenarioKey} defect path does not match its candidate Java file`,
      );
    }

    return {
      ...scenario,
      repositoryUrl: manifest.repositoryUrl,
      datasetVersion: manifest.datasetVersion,
      baseRevision: revision.baseRevision,
      candidateRevision: revision.candidateRevision,
      expectedFilePath,
      projectName: `benchmark-${manifest.datasetVersion}-${scenario.repositoryBranch}`,
    };
  });

  requireCondition(
    revisionByKey.size === scenarios.length,
    "Revision manifest contains scenarios missing from the answer manifest",
  );
  return scenarios;
}

export function selectExistingProject(projects, expected) {
  const exactMatches = projects.filter((project) => project.name === expected.projectName);
  requireCondition(exactMatches.length <= 1, `Multiple projects named ${expected.projectName}`);
  if (exactMatches.length === 0) {
    return null;
  }
  const project = exactMatches[0];
  requireCondition(project.sourceType === "GIT", `${expected.projectName} is not a GIT project`);
  requireCondition(
    project.sourceLocation === expected.repositoryUrl,
    `${expected.projectName} uses a different repository URL`,
  );
  requireCondition(
    project.defaultBranch === expected.repositoryBranch,
    `${expected.projectName} uses a different repository branch`,
  );
  return project;
}

export function verifyImportEvidence(expected, evidence) {
  const { project, importTask, latestImportTask } = evidence;
  const expectedRevision = expected.candidateRevision;

  requireCondition(importTask.status === "SUCCEEDED", `${expected.scenarioKey} import did not succeed`);
  requireCondition(importTask.revision === expectedRevision, `${expected.scenarioKey} import revision drifted`);
  requireCondition(importTask.projectId === project.id, `${expected.scenarioKey} import project ID differs`);
  requireCondition(importTask.totalFiles === 1, `${expected.scenarioKey} import must contain one file`);
  requireCondition(importTask.processedFiles === 1, `${expected.scenarioKey} import did not process one file`);
  requireCondition(importTask.failedFiles === 0, `${expected.scenarioKey} import contains failed files`);
  requireCondition(latestImportTask.id === importTask.id, `${expected.scenarioKey} latest import task differs`);
  requireCondition(latestImportTask.revision === expectedRevision, `${expected.scenarioKey} latest import revision drifted`);

  requireCondition(project.status === "READY", `${expected.scenarioKey} project is not READY`);
  requireCondition(project.currentRevision === expectedRevision, `${expected.scenarioKey} project revision drifted`);
}

export function verifyScenarioEvidence(expected, evidence) {
  const { project, importTask, latestImportTask, reviewDiff, latestReviewDiff } = evidence;
  const expectedRevision = expected.candidateRevision;

  verifyImportEvidence(expected, { project, importTask, latestImportTask });

  requireCondition(reviewDiff.status === "SUCCEEDED", `${expected.scenarioKey} Diff did not succeed`);
  requireCondition(reviewDiff.projectId === project.id, `${expected.scenarioKey} Diff project ID differs`);
  requireCondition(reviewDiff.baseRevision === expected.baseRevision, `${expected.scenarioKey} base revision drifted`);
  requireCondition(reviewDiff.targetRevision === expectedRevision, `${expected.scenarioKey} target revision drifted`);
  requireCondition(reviewDiff.changedFiles === 1, `${expected.scenarioKey} must change exactly one file`);
  requireCondition(reviewDiff.files?.length === 1, `${expected.scenarioKey} Diff must return one file`);
  requireCondition(latestReviewDiff.id === reviewDiff.id, `${expected.scenarioKey} latest Diff task differs`);
  requireCondition(latestReviewDiff.targetRevision === expectedRevision, `${expected.scenarioKey} latest Diff revision drifted`);

  const file = reviewDiff.files[0];
  requireCondition(file.changeType === "MODIFY", `${expected.scenarioKey} must be a MODIFY Diff`);
  requireCondition(file.oldPath === expected.expectedFilePath, `${expected.scenarioKey} old path differs`);
  requireCondition(file.newPath === expected.expectedFilePath, `${expected.scenarioKey} new path differs`);
  requireCondition(file.coverageStatus !== "SKIPPED", `${expected.scenarioKey} Diff coverage was skipped`);
  requireCondition(
    ["FULL", "PARTIAL"].includes(file.coverageStatus),
    `${expected.scenarioKey} returned an unknown coverage status`,
  );
  requireCondition(file.baseChangedLines?.length > 0, `${expected.scenarioKey} has no base changed lines`);
  requireCondition(file.changedLines?.length > 0, `${expected.scenarioKey} has no target changed lines`);
  requireCondition(file.mappedSymbols?.length > 0, `${expected.scenarioKey} has no mapped symbols`);
  requireCondition(reviewDiff.skippedFiles === 0, `${expected.scenarioKey} contains skipped files`);
  requireCondition(
    reviewDiff.fullyMappedFiles + reviewDiff.partiallyMappedFiles + reviewDiff.skippedFiles === 1,
    `${expected.scenarioKey} coverage counters are inconsistent`,
  );
  requireCondition(
    reviewDiff.fullyMappedFiles === (file.coverageStatus === "FULL" ? 1 : 0)
      && reviewDiff.partiallyMappedFiles === (file.coverageStatus === "PARTIAL" ? 1 : 0),
    `${expected.scenarioKey} coverage status does not match its counters`,
  );

  const hasValidRange = (symbol) => (
    Number.isInteger(symbol.startLine)
    && Number.isInteger(symbol.endLine)
    && symbol.startLine > 0
    && symbol.endLine >= symbol.startLine
  );
  const targetSymbols = file.mappedSymbols.filter((symbol) => (
    symbol.revisionSide === "TARGET"
    && typeof symbol.chunkId === "string"
    && symbol.chunkId.length > 0
    && hasValidRange(symbol)
  ));
  const baseSymbols = file.mappedSymbols.filter((symbol) => (
    symbol.revisionSide === "BASE" && hasValidRange(symbol)
  ));
  requireCondition(targetSymbols.length > 0, `${expected.scenarioKey} has no target revision symbol evidence`);

  for (const defect of expected.defects ?? []) {
    const goldRange = { startLine: defect.startLine, endLine: defect.endLine };
    requireCondition(
      file.changedLines.some((changedRange) => rangesOverlap(goldRange, changedRange)),
      `${expected.scenarioKey} gold range does not overlap the target Diff`,
    );
    requireCondition(
      file.changedLines.some((changedRange) => (
        targetSymbols.some((symbol) => rangesShareLine(goldRange, changedRange, symbol))
      )),
      `${expected.scenarioKey} gold range has no mapped target Diff line evidence`,
    );
  }

  const unmappedBaseLines = findUnmappedRanges(file.baseChangedLines, baseSymbols, "BASE");
  const unmappedTargetLines = findUnmappedRanges(file.changedLines, targetSymbols, "TARGET");
  const hasUnmappedLines = unmappedBaseLines.length > 0 || unmappedTargetLines.length > 0;
  requireCondition(
    hasUnmappedLines === (file.coverageStatus === "PARTIAL"),
    `${expected.scenarioKey} coverage status does not match its line evidence`,
  );

  const warnings = [];
  if (file.coverageStatus === "PARTIAL") {
    const sides = [
      ...(unmappedBaseLines.length > 0 ? ["BASE"] : []),
      ...(unmappedTargetLines.length > 0 ? ["TARGET"] : []),
    ];
    warnings.push(`${sides.join(" and ")} Diff lines fall outside the current symbol chunks`);
  }
  return {
    coverageStatus: file.coverageStatus,
    expectedFilePath: expected.expectedFilePath,
    mappedSymbolCount: file.mappedSymbols.length,
    baseChangedLines: file.baseChangedLines,
    changedLines: file.changedLines,
    unmappedBaseLines,
    unmappedTargetLines,
    warnings,
  };
}

function expectedEvaluationCaseCount(scenario) {
  return scenario.expectationType === "CLEAN" ? 1 : (scenario.defects ?? []).length;
}

function initialGoldCaseStatus(scenario, status = "PENDING") {
  return {
    status,
    expectedCount: expectedEvaluationCaseCount(scenario),
    verifiedCount: 0,
    createdCount: 0,
    recoveredCount: 0,
    reusedCount: 0,
    caseIds: [],
    caseKeys: [],
  };
}

function requireManifestText(value, label, options = {}) {
  requireCondition(typeof value === "string" && value.length > 0, `${label} is required`);
  requireCondition(value === value.trim(), `${label} must not contain surrounding whitespace`);
  if (options.maxLength !== undefined) {
    requireCondition(value.length <= options.maxLength, `${label} exceeds ${options.maxLength} characters`);
  }
  if (options.pattern !== undefined) {
    requireCondition(options.pattern.test(value), `${label} contains unsupported characters`);
  }
}

export function buildExpectedEvaluationCases(scenario, result) {
  requireCondition(result.projectId, `${scenario.scenarioKey} project ID is required`);
  requireCondition(result.reviewTaskId, `${scenario.scenarioKey} review task ID is required`);
  requireManifestText(scenario.scenarioKey, "Scenario key", {
    maxLength: 100,
    pattern: EVALUATION_KEY_PATTERN,
  });
  requireManifestText(scenario.datasetVersion, `${scenario.scenarioKey} dataset version`, {
    maxLength: 64,
    pattern: EVALUATION_KEY_PATTERN,
  });
  requireManifestText(scenario.name, `${scenario.scenarioKey} name`, { maxLength: 200 });
  requireManifestText(scenario.rationale, `${scenario.scenarioKey} rationale`, { maxLength: 1000 });
  requireManifestText(
    scenario.candidateRevision,
    `${scenario.scenarioKey} candidate revision`,
    { maxLength: 64 },
  );

  const common = {
    scenarioKey: scenario.scenarioKey,
    projectId: String(result.projectId),
    reviewTaskId: String(result.reviewTaskId),
    datasetVersion: scenario.datasetVersion,
    name: scenario.name,
    targetRevision: scenario.candidateRevision,
  };
  const defects = scenario.defects ?? [];
  if (scenario.expectationType === "CLEAN") {
    requireCondition(defects.length === 0, `${scenario.scenarioKey} CLEAN scenario contains defects`);
    return [{
      ...common,
      caseKey: scenario.scenarioKey,
      expectationType: "CLEAN",
      category: null,
      filePath: null,
      startLine: null,
      endLine: null,
      rationale: scenario.rationale,
    }];
  }

  requireCondition(
    scenario.expectationType === "DEFECT",
    `${scenario.scenarioKey} has an unknown expectation type`,
  );
  requireCondition(defects.length > 0, `${scenario.scenarioKey} DEFECT scenario has no defects`);
  return defects.map((defect) => {
    requireManifestText(defect.caseKey, `${scenario.scenarioKey} defect case key`, {
      maxLength: 100,
      pattern: EVALUATION_KEY_PATTERN,
    });
    requireManifestText(defect.category, `${scenario.scenarioKey}/${defect.caseKey} category`);
    requireCondition(
      EVALUATION_CATEGORIES.has(defect.category),
      `${scenario.scenarioKey}/${defect.caseKey} category is unsupported`,
    );
    requireManifestText(
      defect.filePath,
      `${scenario.scenarioKey}/${defect.caseKey} file path`,
      { maxLength: 1000 },
    );
    requireManifestText(
      defect.rationale,
      `${scenario.scenarioKey}/${defect.caseKey} rationale`,
      { maxLength: 1000 },
    );
    requireCondition(
      Number.isInteger(defect.startLine) && defect.startLine > 0,
      `${scenario.scenarioKey}/${defect.caseKey} start line must be a positive integer`,
    );
    requireCondition(
      Number.isInteger(defect.endLine) && defect.endLine >= defect.startLine,
      `${scenario.scenarioKey}/${defect.caseKey} end line must not precede the start line`,
    );
    return {
      ...common,
      caseKey: defect.caseKey,
      expectationType: "DEFECT",
      category: defect.category,
      filePath: defect.filePath,
      startLine: defect.startLine,
      endLine: defect.endLine,
      rationale: defect.rationale,
    };
  });
}

export function toEvaluationCaseRequest(expected) {
  const request = {
    reviewTaskId: expected.reviewTaskId,
    datasetVersion: expected.datasetVersion,
    caseKey: expected.caseKey,
    name: expected.name,
    expectationType: expected.expectationType,
    rationale: expected.rationale,
  };
  if (expected.expectationType === "DEFECT") {
    Object.assign(request, {
      category: expected.category,
      filePath: expected.filePath,
      startLine: expected.startLine,
      endLine: expected.endLine,
    });
  }
  return request;
}

function requireEvaluationCaseMatch(expected, actual) {
  requireCondition(
    typeof actual?.id === "string" && actual.id.length > 0,
    `${expected.scenarioKey}/${expected.caseKey} saved case ID is not a string`,
  );
  const fields = [
    "projectId",
    "reviewTaskId",
    "datasetVersion",
    "caseKey",
    "name",
    "targetRevision",
    "expectationType",
    "category",
    "filePath",
    "startLine",
    "endLine",
    "rationale",
  ];
  for (const field of fields) {
    const expectedValue = expected[field] ?? null;
    const actualValue = actual[field] ?? null;
    requireCondition(
      actualValue === expectedValue,
      `${expected.scenarioKey}/${expected.caseKey} ${field} differs`,
    );
  }
}

export function planEvaluationCaseSync(expectedCases, actualCases, scenarioKey) {
  requireCondition(expectedCases.length > 0, `${scenarioKey} has no expected evaluation cases`);
  requireCondition(Array.isArray(actualCases), `${scenarioKey} evaluation cases must be an array`);

  const expectedByKey = new Map();
  for (const expected of expectedCases) {
    requireCondition(
      !expectedByKey.has(expected.caseKey),
      `${scenarioKey} contains duplicate expected case key ${expected.caseKey}`,
    );
    expectedByKey.set(expected.caseKey, expected);
  }

  const actualByKey = new Map();
  for (const actual of actualCases) {
    requireCondition(
      !actualByKey.has(actual.caseKey),
      `${scenarioKey} contains duplicate saved case key ${actual.caseKey}`,
    );
    const expected = expectedByKey.get(actual.caseKey);
    requireCondition(expected, `${scenarioKey} contains unexpected saved case ${actual.caseKey}`);
    requireEvaluationCaseMatch(expected, actual);
    actualByKey.set(actual.caseKey, actual);
  }

  return {
    scenarioKey,
    expectedCases,
    existingCases: actualCases,
    missingCases: expectedCases.filter((expected) => !actualByKey.has(expected.caseKey)),
  };
}

export function verifyEvaluationCases(expectedCases, actualCases, scenarioKey) {
  const plan = planEvaluationCaseSync(expectedCases, actualCases, scenarioKey);
  requireCondition(
    plan.missingCases.length === 0,
    `${scenarioKey} is missing ${plan.missingCases.length} evaluation cases`,
  );
  requireCondition(
    actualCases.length === expectedCases.length,
    `${scenarioKey} evaluation case count differs`,
  );
  return [...actualCases].sort((left, right) => left.caseKey.localeCompare(right.caseKey));
}

async function applyEvaluationCasePlan(client, plan) {
  let createdCount = 0;
  let recoveredCount = 0;
  try {
    for (const expected of plan.missingCases) {
      try {
        await client.post(
          `/api/projects/${expected.projectId}/review-evaluation-cases`,
          toEvaluationCaseRequest(expected),
        );
        createdCount += 1;
      } catch (error) {
        const current = await client.listReviewEvaluationCases(
          expected.projectId,
          expected.datasetVersion,
        );
        const recoveryPlan = planEvaluationCaseSync(
          plan.expectedCases,
          current,
          plan.scenarioKey,
        );
        if (recoveryPlan.missingCases.some((value) => value.caseKey === expected.caseKey)) {
          throw error;
        }
        recoveredCount += 1;
      }
    }

    const saved = await client.listReviewEvaluationCases(
      plan.expectedCases[0].projectId,
      plan.expectedCases[0].datasetVersion,
    );
    const verified = verifyEvaluationCases(plan.expectedCases, saved, plan.scenarioKey);
    return {
      scenarioKey: plan.scenarioKey,
      status: "VERIFIED",
      expectedCount: plan.expectedCases.length,
      verifiedCount: verified.length,
      createdCount,
      recoveredCount,
      reusedCount: plan.existingCases.length,
      caseIds: verified.map((value) => value.id),
      caseKeys: verified.map((value) => value.caseKey),
    };
  } catch (error) {
    const failure = error instanceof Error ? error : new VerificationError(String(error));
    failure.evaluationOutcome = {
      scenarioKey: plan.scenarioKey,
      status: "FAILED",
      expectedCount: plan.expectedCases.length,
      verifiedCount: 0,
      createdCount,
      recoveredCount,
      reusedCount: plan.existingCases.length,
      caseIds: plan.existingCases.map((value) => value.id),
      caseKeys: plan.existingCases.map((value) => value.caseKey),
    };
    throw failure;
  }
}

export async function syncEvaluationCaseBatch(client, entries) {
  const plans = [];
  for (const entry of entries) {
    try {
      const expectedCases = buildExpectedEvaluationCases(entry.scenario, entry.result);
      const actualCases = await client.listReviewEvaluationCases(
        entry.result.projectId,
        entry.scenario.datasetVersion,
      );
      plans.push(planEvaluationCaseSync(
        expectedCases,
        actualCases,
        entry.scenario.scenarioKey,
      ));
    } catch (error) {
      return {
        outcomes: [],
        failure: {
          scenarioKey: entry.scenario.scenarioKey,
          phase: "PREFLIGHT",
          error: error instanceof Error ? error.message : String(error),
        },
      };
    }
  }

  const outcomes = [];
  for (const plan of plans) {
    try {
      outcomes.push(await applyEvaluationCasePlan(client, plan));
    } catch (error) {
      if (error?.evaluationOutcome) {
        outcomes.push(error.evaluationOutcome);
      }
      return {
        outcomes,
        failure: {
          scenarioKey: plan.scenarioKey,
          phase: "APPLY",
          error: error instanceof Error ? error.message : String(error),
        },
      };
    }
  }
  return { outcomes, failure: null };
}

export class DevMateClient {
  constructor(baseUrl, fetchImplementation = globalThis.fetch, timeoutMs = DEFAULT_TIMEOUT_MS) {
    requireCondition(typeof fetchImplementation === "function", "A fetch implementation is required");
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.fetchImplementation = fetchImplementation;
    this.timeoutMs = timeoutMs;
  }

  async request(method, requestPath, body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await this.fetchImplementation(`${this.baseUrl}${requestPath}`, {
        method,
        headers: body === undefined ? undefined : { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
      const responseText = await response.text();
      let payload;
      try {
        payload = JSON.parse(responseText);
      } catch {
        throw new VerificationError(`${method} ${requestPath} returned a non-JSON response`);
      }
      if (!response.ok || payload.code !== 0) {
        throw new VerificationError(
          `${method} ${requestPath} failed: ${payload.message ?? `HTTP ${response.status}`}`,
        );
      }
      return payload.data;
    } catch (error) {
      if (error?.name === "AbortError") {
        throw new VerificationError(`${method} ${requestPath} timed out`);
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  get(requestPath) {
    return this.request("GET", requestPath);
  }

  post(requestPath, body) {
    return this.request("POST", requestPath, body);
  }

  async listProjectsByName(name) {
    const projects = [];
    let page = 1;
    let totalPages = 1;
    do {
      const query = new URLSearchParams({ name, page: String(page), size: "100" });
      const response = await this.get(`/api/projects?${query}`);
      projects.push(...response.items);
      totalPages = response.pages;
      page += 1;
      requireCondition(page <= 101, "Project search exceeded 100 pages");
    } while (page <= totalPages);
    return projects;
  }

  listReviewEvaluationCases(projectId, datasetVersion, reviewTaskId = null) {
    const query = new URLSearchParams({ datasetVersion });
    if (reviewTaskId !== null) {
      query.set("reviewTaskId", String(reviewTaskId));
    }
    return this.get(`/api/projects/${projectId}/review-evaluation-cases?${query}`);
  }
}

async function loadScenarioPlan() {
  const [manifest, revisions] = await Promise.all([
    readJson(path.join(FIXTURE_ROOT, "manifest.json")),
    readJson(path.join(FIXTURE_ROOT, "revisions.json")),
  ]);
  const candidatePathsByScenario = new Map();
  for (const scenario of manifest.scenarios) {
    const candidateRoot = path.join(FIXTURE_ROOT, "scenarios", scenario.scenarioKey, "candidate");
    candidatePathsByScenario.set(scenario.scenarioKey, await listJavaFiles(candidateRoot));
  }
  return {
    datasetVersion: manifest.datasetVersion,
    repositoryUrl: manifest.repositoryUrl,
    scenarios: mergeScenarioDefinitions(manifest, revisions, candidatePathsByScenario),
  };
}

async function ensureProject(client, scenario, allowCreate) {
  const projects = await client.listProjectsByName(scenario.projectName);
  const existing = selectExistingProject(projects, scenario);
  if (existing) {
    return { project: existing, reused: true };
  }
  requireCondition(
    allowCreate,
    `${scenario.projectName} does not exist; run without --reuse-imports first`,
  );
  const project = await client.post("/api/projects", {
    name: scenario.projectName,
    description: `${scenario.datasetVersion} live import and Diff verification`,
    sourceType: "GIT",
    sourceLocation: scenario.repositoryUrl,
    defaultBranch: scenario.repositoryBranch,
  });
  return { project, reused: false };
}

export async function verifyOneScenario(client, scenario, options = {}) {
  const reuseImports = options.reuseImports ?? false;
  const reuseDiffs = options.reuseDiffs ?? false;
  requireCondition(!reuseDiffs || reuseImports, "Reusing Diffs requires reusing imports");
  const { project: initialProject, reused } = await ensureProject(client, scenario, !reuseImports);
  const projectId = initialProject.id;
  const importTask = reuseImports
    ? await client.get(`/api/projects/${projectId}/imports/latest`)
    : await client.post(`/api/projects/${projectId}/imports`);
  const latestImportTask = await client.get(`/api/projects/${projectId}/imports/latest`);
  const project = await client.get(`/api/projects/${projectId}`);
  verifyImportEvidence(scenario, { project, importTask, latestImportTask });
  const reviewDiff = reuseDiffs
    ? await client.get(`/api/projects/${projectId}/review-diffs/latest`)
    : await client.post(`/api/projects/${projectId}/review-diffs`, {});
  const latestReviewDiff = reuseDiffs
    ? reviewDiff
    : await client.get(`/api/projects/${projectId}/review-diffs/latest`);
  const verified = verifyScenarioEvidence(scenario, {
    project,
    importTask,
    latestImportTask,
    reviewDiff,
    latestReviewDiff,
  });
  return {
    scenarioKey: scenario.scenarioKey,
    repositoryBranch: scenario.repositoryBranch,
    projectId,
    projectReused: reused,
    importTriggered: !reuseImports,
    diffTriggered: !reuseDiffs,
    importTaskId: importTask.id,
    reviewTaskId: reviewDiff.id,
    expectedBaseRevision: scenario.baseRevision,
    actualBaseRevision: reviewDiff.baseRevision,
    expectedCandidateRevision: scenario.candidateRevision,
    actualCandidateRevision: reviewDiff.targetRevision,
    status: "PASS",
    ...verified,
  };
}

export function parseArguments(args) {
  const options = {
    baseUrl: process.env.DEVMATE_BASE_URL ?? DEFAULT_BASE_URL,
    reportPath: DEFAULT_REPORT_PATH,
    scenario: null,
    reuseImports: false,
    reuseDiffs: false,
    recordGoldCases: false,
  };
  const readValue = (index, option) => {
    const value = args[index + 1];
    requireCondition(value && !value.startsWith("--"), `${option} requires a value`);
    return value;
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
    } else if (argument === "--reuse-imports") {
      options.reuseImports = true;
    } else if (argument === "--reuse-diffs") {
      options.reuseDiffs = true;
    } else if (argument === "--record-gold-cases") {
      options.recordGoldCases = true;
    } else if (argument === "--help") {
      options.help = true;
    } else {
      throw new VerificationError(`Unknown argument: ${argument}`);
    }
  }
  requireCondition(options.baseUrl, "--base-url requires a value");
  requireCondition(options.reportPath, "--report requires a value");
  requireCondition(!options.reuseDiffs || options.reuseImports, "--reuse-diffs requires --reuse-imports");
  requireCondition(
    !options.recordGoldCases || options.reuseDiffs,
    "--record-gold-cases requires --reuse-diffs",
  );
  requireCondition(
    !options.recordGoldCases || !options.scenario,
    "--record-gold-cases cannot be combined with --scenario",
  );
  return options;
}

function printHelp() {
  console.log(`Usage: node benchmarks/review-fixtures/verify-live-imports.mjs [options]

Options:
  --base-url URL   DevMate API URL (default: ${DEFAULT_BASE_URL})
  --report PATH    JSON report path (default: target/benchmark-results/...)
  --scenario KEY   Run one scenario key or case-NNN branch
  --reuse-imports  Require and verify each project's latest successful import
  --reuse-diffs    Reuse and verify each project's latest Diff; requires --reuse-imports
  --record-gold-cases
                   Record or verify all manifest cases; requires --reuse-diffs and all scenarios
  --help           Show this help

This command imports all public benchmark branches and creates HEAD^ -> HEAD Diffs.
Gold-case mode reuses existing imports and Diffs before writing evaluation cases.
It never calls embedding or AI endpoints.`);
}

async function writeReport(reportPath, report) {
  await mkdir(path.dirname(reportPath), { recursive: true });
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

export async function runLiveVerification(options, dependencies = {}) {
  requireCondition(!options.reuseDiffs || options.reuseImports, "Reusing Diffs requires reusing imports");
  requireCondition(!options.recordGoldCases || options.reuseDiffs, "Recording gold cases requires reused Diffs");
  requireCondition(
    !options.recordGoldCases || !options.scenario,
    "Recording gold cases requires the full dataset",
  );
  const plan = dependencies.plan ?? await loadScenarioPlan();
  const client = dependencies.client ?? new DevMateClient(options.baseUrl);
  const reportWriter = dependencies.reportWriter ?? writeReport;
  const startedAt = new Date().toISOString();
  const results = [];
  const scenarios = options.scenario
    ? plan.scenarios.filter((scenario) => (
      scenario.scenarioKey === options.scenario || scenario.repositoryBranch === options.scenario
    ))
    : plan.scenarios;
  requireCondition(scenarios.length === 1 || !options.scenario, `Unknown scenario: ${options.scenario}`);

  const health = await client.get("/api/health");
  requireCondition(health?.status === "UP", "DevMate health endpoint is not UP");

  for (const scenario of scenarios) {
    process.stdout.write(`[${scenario.repositoryBranch}] ${scenario.scenarioKey} ... `);
    try {
      const result = await verifyOneScenario(client, scenario, options);
      if (options.recordGoldCases) {
        result.goldCases = initialGoldCaseStatus(scenario);
      }
      results.push(result);
      console.log(result.warnings.length === 0 ? "PASS" : `PASS (${result.coverageStatus})`);
    } catch (error) {
      const failedResult = {
        scenarioKey: scenario.scenarioKey,
        repositoryBranch: scenario.repositoryBranch,
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error),
      };
      if (options.recordGoldCases) {
        failedResult.goldCases = initialGoldCaseStatus(scenario, "NOT_APPLIED");
      }
      results.push(failedResult);
      console.log("FAIL");
    }
  }

  if (options.recordGoldCases) {
    const resultByScenario = new Map(results.map((result) => [result.scenarioKey, result]));
    if (results.every((result) => result.status === "PASS")) {
      console.log("[evaluation-cases] preflight and record manifest cases ...");
      const sync = await syncEvaluationCaseBatch(
        client,
        scenarios.map((scenario) => ({
          scenario,
          result: resultByScenario.get(scenario.scenarioKey),
        })),
      );
      for (const outcome of sync.outcomes) {
        resultByScenario.get(outcome.scenarioKey).goldCases = outcome;
      }
      if (sync.failure) {
        const failed = resultByScenario.get(sync.failure.scenarioKey);
        failed.status = "FAIL";
        failed.failurePhase = `EVALUATION_CASE_${sync.failure.phase}`;
        failed.error = sync.failure.error;
        failed.goldCases = {
          ...failed.goldCases,
          status: "FAILED",
          failurePhase: sync.failure.phase,
          error: sync.failure.error,
        };
      }
      for (const result of results) {
        if (result.goldCases.status === "PENDING") {
          result.goldCases.status = "NOT_APPLIED";
        }
      }
    } else {
      for (const result of results) {
        if (result.goldCases?.status === "PENDING") {
          result.goldCases.status = "NOT_APPLIED";
        }
      }
    }
  }

  const report = {
    datasetVersion: plan.datasetVersion,
    repositoryUrl: plan.repositoryUrl,
    baseUrl: options.baseUrl,
    importMode: options.reuseImports ? "REUSE_LATEST_SUCCEEDED" : "TRIGGER_IMPORT",
    diffMode: options.reuseDiffs ? "REUSE_LATEST" : "CREATE",
    goldCaseMode: options.recordGoldCases ? "VERIFY_OR_CREATE" : "NOT_REQUESTED",
    startedAt,
    finishedAt: new Date().toISOString(),
    summary: {
      total: results.length,
      passed: results.filter((result) => result.status === "PASS").length,
      failed: results.filter((result) => result.status === "FAIL").length,
      fullCoverage: results.filter((result) => result.coverageStatus === "FULL").length,
      partialCoverage: results.filter((result) => result.coverageStatus === "PARTIAL").length,
      goldCasesExpected: results.reduce(
        (total, result) => total + (result.goldCases?.expectedCount ?? 0),
        0,
      ),
      goldCasesVerified: results.reduce(
        (total, result) => total + (result.goldCases?.verifiedCount ?? 0),
        0,
      ),
      goldCasesCreated: results.reduce(
        (total, result) => total + (result.goldCases?.createdCount ?? 0),
        0,
      ),
      goldCasesRecovered: results.reduce(
        (total, result) => total + (result.goldCases?.recoveredCount ?? 0),
        0,
      ),
      goldCasesReused: results.reduce(
        (total, result) => total + (result.goldCases?.reusedCount ?? 0),
        0,
      ),
    },
    results,
  };
  await reportWriter(options.reportPath, report);
  console.log(`Report: ${options.reportPath}`);
  requireCondition(report.summary.failed === 0, `${report.summary.failed} benchmark scenarios failed`);
  requireCondition(
    !options.recordGoldCases
      || report.summary.goldCasesVerified === report.summary.goldCasesExpected,
    "Not all manifest evaluation cases were verified",
  );
  return report;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  await runLiveVerification(options);
}

if (process.argv[1] && path.resolve(process.argv[1]) === SCRIPT_PATH) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
