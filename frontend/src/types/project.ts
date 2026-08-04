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
  sourceKind: 'SOURCE_CODE' | 'CONFIGURATION'
  fileType: 'JAVA' | 'YAML' | 'PROPERTIES'
  packageName?: string
  revision: string
  status: 'PARSED' | 'FAILED'
  chunkCount: number
}

export type SourceSymbolType = 'CLASS' | 'CONSTRUCTOR' | 'METHOD' | 'CONFIG_PROPERTY'

export interface SourceSymbol {
  id: string
  documentId: string
  chunkType: SourceSymbolType
  symbolName: string
  annotations: string[]
  startLine: number
  endLine: number
  contentHash: string
  revision: string
}

export type SourceReferenceKind = 'METHOD_CALL' | 'DATA_ACCESS' | 'CONFIG_KEY' | 'CONFIG_PREFIX'

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

export type FindingSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

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
