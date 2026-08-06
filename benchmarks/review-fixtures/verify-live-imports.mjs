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

export async function verifyOneScenario(client, scenario, reuseImports = false) {
  const { project: initialProject, reused } = await ensureProject(client, scenario, !reuseImports);
  const projectId = initialProject.id;
  const importTask = reuseImports
    ? await client.get(`/api/projects/${projectId}/imports/latest`)
    : await client.post(`/api/projects/${projectId}/imports`);
  const latestImportTask = await client.get(`/api/projects/${projectId}/imports/latest`);
  const project = await client.get(`/api/projects/${projectId}`);
  verifyImportEvidence(scenario, { project, importTask, latestImportTask });
  const reviewDiff = await client.post(`/api/projects/${projectId}/review-diffs`, {});
  const latestReviewDiff = await client.get(`/api/projects/${projectId}/review-diffs/latest`);
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
    } else if (argument === "--help") {
      options.help = true;
    } else {
      throw new VerificationError(`Unknown argument: ${argument}`);
    }
  }
  requireCondition(options.baseUrl, "--base-url requires a value");
  requireCondition(options.reportPath, "--report requires a value");
  return options;
}

function printHelp() {
  console.log(`Usage: node benchmarks/review-fixtures/verify-live-imports.mjs [options]

Options:
  --base-url URL   DevMate API URL (default: ${DEFAULT_BASE_URL})
  --report PATH    JSON report path (default: target/benchmark-results/...)
  --scenario KEY   Run one scenario key or case-NNN branch
  --reuse-imports  Require and verify each project's latest successful import
  --help           Show this help

This command imports all public benchmark branches and creates HEAD^ -> HEAD Diffs.
It never calls embedding or AI endpoints.`);
}

async function writeReport(reportPath, report) {
  await mkdir(path.dirname(reportPath), { recursive: true });
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

export async function runLiveVerification(options) {
  const plan = await loadScenarioPlan();
  const client = new DevMateClient(options.baseUrl);
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
      const result = await verifyOneScenario(client, scenario, options.reuseImports);
      results.push(result);
      console.log(result.warnings.length === 0 ? "PASS" : `PASS (${result.coverageStatus})`);
    } catch (error) {
      results.push({
        scenarioKey: scenario.scenarioKey,
        repositoryBranch: scenario.repositoryBranch,
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error),
      });
      console.log("FAIL");
    }
  }

  const report = {
    datasetVersion: plan.datasetVersion,
    repositoryUrl: plan.repositoryUrl,
    baseUrl: options.baseUrl,
    importMode: options.reuseImports ? "REUSE_LATEST_SUCCEEDED" : "TRIGGER_IMPORT",
    startedAt,
    finishedAt: new Date().toISOString(),
    summary: {
      total: results.length,
      passed: results.filter((result) => result.status === "PASS").length,
      failed: results.filter((result) => result.status === "FAIL").length,
      fullCoverage: results.filter((result) => result.coverageStatus === "FULL").length,
      partialCoverage: results.filter((result) => result.coverageStatus === "PARTIAL").length,
    },
    results,
  };
  await writeReport(options.reportPath, report);
  console.log(`Report: ${options.reportPath}`);
  requireCondition(report.summary.failed === 0, `${report.summary.failed} benchmark scenarios failed`);
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
