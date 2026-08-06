export type SourceType = 'LOCAL' | 'GIT' | 'UPLOAD'
export type ProjectStatus = 'CREATED' | 'INDEXING' | 'READY' | 'FAILED'

export interface Project {
  id: string
  name: string
  description?: string
  sourceType: SourceType
  sourceLocation?: string
  defaultBranch?: string
  currentRevision?: string
  status: ProjectStatus
  createdAt: string
  updatedAt: string
  lastIndexedAt?: string
}

export interface ProjectForm {
  name: string
  description: string
  sourceType: SourceType
  sourceLocation: string
  defaultBranch: string
}

export type IndexTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface EmbeddingIndexTask {
  id: string
  projectId: string
  revision: string
  provider: string
  modelName: string
  dimensions: number
  status: IndexTaskStatus
  totalChunks: number
  processedChunks: number
  skippedChunks: number
  reusedChunks: number
  failedChunks: number
  errorMessage?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface IndexTask {
  id: string
  projectId: string
  taskType: 'FULL' | 'INCREMENTAL'
  revision?: string
  status: IndexTaskStatus
  totalFiles: number
  processedFiles: number
  failedFiles: number
  errorMessage?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface SourceDocument {
  id: string
  fileName: string
  filePath: string
  sourceKind: 'SOURCE_CODE' | 'CONFIGURATION' | 'DATABASE_SCHEMA'
  fileType: 'JAVA' | 'YAML' | 'PROPERTIES' | 'SQL'
  packageName?: string
  revision: string
  status: 'PARSED' | 'FAILED'
  chunkCount: number
}

export type SourceSymbolType =
  | 'CLASS'
  | 'CONSTRUCTOR'
  | 'METHOD'
  | 'CONFIG_PROPERTY'
  | 'DATABASE_TABLE'
  | 'DATABASE_COLUMN'
  | 'DATABASE_INDEX'
  | 'DATABASE_CONSTRAINT'
  | 'DATABASE_CHANGE'

export interface SourceSymbol {
  id: string
  documentId: string
  chunkType: SourceSymbolType
  symbolName: string
  summary?: string
  annotations: string[]
  startLine: number
  endLine: number
  contentHash: string
  revision: string
}

export type SourceReferenceKind =
  | 'METHOD_CALL'
  | 'DATA_ACCESS'
  | 'CONFIG_KEY'
  | 'CONFIG_PREFIX'
  | 'DATABASE_TABLE'

export interface SourceReference {
  id: string
  referenceKind: SourceReferenceKind
  referenceName: string
  qualifier?: string
  argumentCount?: number
  sourceChunkId: string
  sourceSymbolName: string
  sourceFilePath?: string
  targetChunkId?: string
  targetSymbolName?: string
  targetFilePath?: string
  startLine: number
  endLine: number
  resolved: boolean
}

export interface LineRange {
  startLine: number
  endLine: number
}

export interface MappedSymbol {
  chunkId?: string
  revisionSide: 'BASE' | 'TARGET'
  chunkType: SourceSymbolType
  symbolName: string
  startLine: number
  endLine: number
}

export type CoverageStatus = 'FULL' | 'PARTIAL' | 'SKIPPED'

export interface ReviewFile {
  id: string
  oldPath?: string
  newPath?: string
  changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME' | 'COPY'
  coverageStatus: CoverageStatus
  additions: number
  deletions: number
  baseChangedLines: LineRange[]
  changedLines: LineRange[]
  mappedSymbols: MappedSymbol[]
  skipReason?: string
}

export interface ReviewDiff {
  id: string
  projectId: string
  baseRevision?: string
  targetRevision?: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  changedFiles: number
  fullyMappedFiles: number
  partiallyMappedFiles: number
  skippedFiles: number
  errorMessage?: string
  createdAt: string
  finishedAt?: string
  files: ReviewFile[]
}

export interface CreateAiReviewRequest {
  reviewTaskId: string
  revision: string
  attemptKey: string
}

export type FindingSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface StaticFinding {
  id: string
  source: 'STATIC'
  ruleId: string
  category: string
  severity: FindingSeverity
  filePath: string
  startLine: number
  endLine: number
  message: string
  evidence: string
}

export interface StaticAnalysis {
  id: string
  projectId: string
  reviewTaskId: string
  toolName: string
  toolVersion: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  analyzedFiles: number
  findingCount: number
  errorMessage?: string
  createdAt: string
  finishedAt?: string
  findings: StaticFinding[]
}

export type AiConclusionType = 'FACT' | 'INFERENCE' | 'NEEDS_VERIFICATION'
export type ReviewFeedbackType = 'ACCEPTED' | 'REJECTED' | 'FALSE_POSITIVE' | 'DEFERRED'

export interface ReviewFeedback {
  id: string
  projectId: string
  findingId: string
  feedbackType: ReviewFeedbackType
  comment?: string
  createdAt: string
  updatedAt: string
}

export interface ReviewFeedbackForm {
  feedbackType: ReviewFeedbackType
  comment?: string
}

export interface AiReviewFinding {
  id: string
  chunkId: string
  source: 'LLM'
  category: string
  severity: FindingSeverity
  conclusionType: AiConclusionType
  confidence: number
  filePath: string
  startLine: number
  endLine: number
  title: string
  evidence: string
  riskScenario: string
  suggestion: string
  verification: string
  feedback?: ReviewFeedback
}

export interface ToolCall {
  id: string
  toolCallId: string
  stepNo: number
  toolName: string
  argumentsSummary: string
  resultSummary?: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  latencyMs: number
  errorMessage?: string
  createdAt: string
}

export interface AiReview {
  id: string
  projectId: string
  reviewTaskId: string
  staticAnalysisTaskId: string
  invocationId: string
  attemptKey: string
  revision: string
  provider: string
  modelName: string
  promptVersion: string
  executionMode: 'FIXED' | 'AGENT'
  retrievalConfigVersion?: string
  retrievalMode?: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  contextChunks: number
  findingCount: number
  rejectedFindings: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  latencyMs: number
  errorMessage?: string
  createdAt: string
  finishedAt?: string
  findings: AiReviewFinding[]
  toolCalls: ToolCall[]
}

export type AiFindingCategory =
  | 'CONCURRENCY'
  | 'TRANSACTION'
  | 'CACHE'
  | 'MESSAGE'
  | 'SQL'
  | 'SECURITY'
  | 'ARCHITECTURE'
  | 'PERFORMANCE'
  | 'RELIABILITY'

export type ReviewExpectationType = 'DEFECT' | 'CLEAN'

export interface ReviewEvaluationCaseForm {
  reviewTaskId: string
  datasetVersion: string
  caseKey: string
  name: string
  expectationType: ReviewExpectationType
  category?: AiFindingCategory
  filePath?: string
  startLine?: number
  endLine?: number
  rationale: string
}

export interface ReviewEvaluationCase extends ReviewEvaluationCaseForm {
  id: string
  projectId: string
  targetRevision: string
  createdAt: string
  updatedAt: string
}

export type ReviewEvaluationOutcome =
  | 'TRUE_POSITIVE'
  | 'FALSE_POSITIVE'
  | 'FALSE_NEGATIVE'
  | 'MANUAL_REVIEW'
  | 'CLEAN_PASS'

export interface ReviewEvaluationItemResult {
  expectedCaseId?: string
  findingId?: string
  outcome: ReviewEvaluationOutcome
  reason: string
}

export interface ReviewEvaluationRun {
  id: string
  projectId: string
  reviewTaskId: string
  aiReviewTaskId: string
  datasetVersion: string
  datasetHash: string
  executionMode: 'FIXED' | 'AGENT'
  revision: string
  modelName: string
  promptVersion: string
  retrievalConfigVersion?: string
  status: 'SUCCEEDED' | 'FAILED'
  expectedDefects: number
  predictedFindings: number
  truePositives: number
  falsePositives: number
  falseNegatives: number
  manualReviewCount: number
  partialMetrics: boolean
  precision: number
  recall: number
  f1: number
  totalTokens: number
  latencyMs: number
  toolCallCount: number
  toolSuccessCount: number
  createdAt: string
  finishedAt?: string
  results: ReviewEvaluationItemResult[]
}

export type RetrievalTrimReason = 'DUPLICATE_CONTENT' | 'TOKEN_BUDGET' | 'TOP_K'
export type RetrievalMode = 'LEXICAL' | 'VECTOR' | 'HYBRID'

export interface RetrievalHit {
  chunkId: string
  documentId: string
  filePath: string
  sourceKind: 'SOURCE_CODE' | 'CONFIGURATION' | 'DATABASE_SCHEMA'
  chunkType: SourceSymbolType
  symbolName?: string
  startLine?: number
  endLine?: number
  score: number
  estimatedTokens: number
  reasons: string[]
  excerpt: string
}

export interface RetrievalTrimmed {
  chunkId: string
  filePath: string
  symbolName?: string
  estimatedTokens: number
  reason: RetrievalTrimReason
}

export interface RetrievalSearch {
  projectId: string
  revision: string
  query: string
  configVersion: string
  requestedMode: RetrievalMode
  executedMode: RetrievalMode | 'LEXICAL_FALLBACK'
  embeddingProvider: string
  embeddingModel: string
  vectorIndexAvailable: boolean
  vectorCandidateCount: number
  vectorLimitReached: boolean
  degradationReason?: string
  candidateCount: number
  candidateLimitReached: boolean
  referenceLimitReached: boolean
  topK: number
  tokenBudget: number
  usedTokens: number
  selectedCount: number
  trimmedCount: number
  omittedTrimmedDetails: number
  hits: RetrievalHit[]
  trimmed: RetrievalTrimmed[]
}

export interface RetrievalSearchForm {
  query: string
  topK: number
  tokenBudget: number
  retrievalMode: RetrievalMode
}

export interface ProjectQuery {
  page: number
  size: number
  name?: string
  status?: ProjectStatus | ''
}

export interface PageData<T> {
  page: number
  size: number
  total: number
  pages: number
  items: T[]
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}
