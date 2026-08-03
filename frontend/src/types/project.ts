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
