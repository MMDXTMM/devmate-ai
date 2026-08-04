import type {
  ApiResponse,
  IndexTask,
  PageData,
  Project,
  ProjectForm,
  ProjectQuery,
  SourceDocument,
  SourceSymbol,
  ReviewDiff,
} from '../types/project'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code?: number,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动')
  }

  let body: ApiResponse<T> | undefined
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError('后端返回了无法解析的响应', undefined, response.status)
  }

  if (!response.ok || body.code !== 0) {
    throw new ApiError(body.message || '请求失败', body.code, response.status)
  }
  return body.data
}

export const projectApi = {
  list(query: ProjectQuery): Promise<PageData<Project>> {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    })
    if (query.name?.trim()) params.set('name', query.name.trim())
    if (query.status) params.set('status', query.status)
    return request(`/api/projects?${params.toString()}`)
  },

  create(form: ProjectForm): Promise<Project> {
    return request('/api/projects', {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  update(id: string, form: ProjectForm): Promise<Project> {
    return request(`/api/projects/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(form),
    })
  },

  delete(id: string): Promise<void> {
    return request(`/api/projects/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
  },

  importSource(id: string): Promise<IndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/imports`, {
      method: 'POST',
    })
  },

  latestImport(id: string): Promise<IndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/imports/latest`)
  },

  listSourceDocuments(id: string): Promise<SourceDocument[]> {
    return request(`/api/projects/${encodeURIComponent(id)}/sources`)
  },

  listSourceSymbols(projectId: string, documentId: string): Promise<SourceSymbol[]> {
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/sources/${encodeURIComponent(documentId)}/symbols`,
    )
  },

  createReviewDiff(id: string): Promise<ReviewDiff> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-diffs`, {
      method: 'POST',
      body: '{}',
    })
  },

  latestReviewDiff(id: string): Promise<ReviewDiff> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-diffs/latest`)
  },
}
