import { apiRequest } from './apiClient'
import type { ClarificationAnswerForm, GenerationSession } from '../types/generation'

export const generationApi = {
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
  ): Promise<GenerationSession> {
    return apiRequest(
      `/api/generation-sessions/${encodeURIComponent(sessionId)}/clarifications`,
      {
        method: 'POST',
        body: JSON.stringify({ answers }),
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
