import type {
  AgentConversation,
  ConversationMessage,
  ConversationStreamHandlers,
} from '../types/conversation'
import type { ApiResponse } from '../types/project'
import { clearAuthSession, getAuthSession } from './authSession'
import { ApiError, apiRequest } from './apiClient'

export const conversationApi = {
  create(projectId: string, title?: string): Promise<AgentConversation> {
    return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/agent-conversations`, {
      method: 'POST',
      body: JSON.stringify({ title: title || null }),
    })
  },

  list(projectId: string): Promise<AgentConversation[]> {
    return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/agent-conversations`)
  },

  messages(projectId: string, conversationId: string): Promise<ConversationMessage[]> {
    return apiRequest(
      `/api/projects/${encodeURIComponent(projectId)}/agent-conversations/${encodeURIComponent(conversationId)}/messages`,
    )
  },

  async streamMessage(
    projectId: string,
    conversationId: string,
    question: string,
    attemptKey: string,
    handlers: ConversationStreamHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const session = getAuthSession()
    let response: Response
    try {
      response = await fetch(
        `/api/projects/${encodeURIComponent(projectId)}/agent-conversations/${encodeURIComponent(conversationId)}/messages/stream`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(session ? { Authorization: `${session.tokenType} ${session.accessToken}` } : {}),
          },
          body: JSON.stringify({ question, attemptKey }),
          signal,
        },
      )
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动')
    }
    if (!response.ok) {
      if (response.status === 401) clearAuthSession()
      let message = '项目对话请求失败'
      try {
        const body = await response.json() as ApiResponse<never>
        if (body.message) message = body.message
      } catch { /* keep readable fallback */ }
      throw new ApiError(message, undefined, response.status)
    }
    if (!response.body) throw new ApiError('浏览器没有收到流式响应')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        dispatchEventBlock(buffer.slice(0, boundary), handlers)
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
      if (done) break
    }
    if (buffer.trim()) dispatchEventBlock(buffer, handlers)
  },
}

function dispatchEventBlock(block: string, handlers: ConversationStreamHandlers) {
  let event = 'message'
  const data: string[] = []
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  })
  if (!data.length) return
  let value: any
  try {
    value = JSON.parse(data.join('\n'))
  } catch {
    // A truncated event block must not discard an answer the backend already persisted.
    return
  }
  if (event === 'message') handlers.onMessage?.(value)
  else if (event === 'evidence') handlers.onEvidence?.(value)
  else if (event === 'token') handlers.onToken?.(value)
  else if (event === 'done') handlers.onDone?.(value)
  else if (event === 'failed') handlers.onFailed?.(value)
}
