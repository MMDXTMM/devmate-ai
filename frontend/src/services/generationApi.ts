import { apiRequest, ApiError } from './apiClient'
import { clearAuthSession, getAuthSession } from './authSession'
import type { ClarificationAnswerForm, GenerationSession, GenerationRun } from '../types/generation'

export const generationApi = {
  list: () => apiRequest<GenerationSession[]>('/api/generation-sessions'),
  latestRun: (sessionId: string) => apiRequest<GenerationRun | null>(`/api/generation-sessions/${encodeURIComponent(sessionId)}/runs/latest`),
  generate: (sessionId: string, versionId: string, attemptKey: string) => apiRequest<GenerationRun>(`/api/generation-sessions/${encodeURIComponent(sessionId)}/runs`, {
    method: 'POST', body: JSON.stringify({ versionId, attemptKey }),
  }),
  file: (sessionId: string, runId: string, path: string) => apiRequest<{ path: string; content: string }>(`/api/generation-sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}/files?${new URLSearchParams({ path })}`),
  async download(sessionId: string, runId: string): Promise<Blob> {
    const auth = getAuthSession()
    let response: Response
    try {
      response = await fetch(`/api/generation-sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}/download`, {
        headers: auth ? { Authorization: `${auth.tokenType} ${auth.accessToken}` } : {},
        signal: AbortSignal.timeout(30000),
      })
    } catch { throw new ApiError('下载失败，请检查后端服务后重试') }
    if (!response.ok) {
      if (response.status === 401) clearAuthSession()
      const body = await response.json().catch(() => null)
      throw new ApiError(body?.message || `下载失败（HTTP ${response.status}）`)
    }
    return response.blob()
  },
  create(requirement: string): Promise<GenerationSession> {
    return apiRequest('/api/generation-sessions', {
      method: 'POST',
      body: JSON.stringify({ requirement }),
    })
  },

  get(sessionId: string): Promise<GenerationSession> {
    return apiRequest(`/api/generation-sessions/${encodeURIComponent(sessionId)}`)
  },

  clarify(
    sessionId: string,
    answers: ClarificationAnswerForm[],
    versionId?: string,
  ): Promise<GenerationSession> {
    return apiRequest(
      `/api/generation-sessions/${encodeURIComponent(sessionId)}/clarifications`,
      {
        method: 'POST',
        body: JSON.stringify({ answers, versionId }),
      },
    )
  },

  confirm(sessionId: string, versionId: string): Promise<GenerationSession> {
    return apiRequest(
      `/api/generation-sessions/${encodeURIComponent(sessionId)}/confirmations`,
      {
        method: 'POST',
        body: JSON.stringify({ versionId }),
      },
    )
  },
}
