import type { ApiResponse } from '../types/project'
import { clearAuthSession, getAuthSession } from './authSession'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code?: number,
    readonly status?: number,
    readonly requestId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const REQUEST_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/

function readRequestId(response: Response): string | undefined {
  const requestId = response.headers.get('X-Request-Id')
  return requestId && REQUEST_ID_PATTERN.test(requestId) ? requestId : undefined
}

function errorMessage(message: string, requestId?: string): string {
  return requestId ? `${message}（请求ID：${requestId}）` : message
}

export async function apiRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const session = getAuthSession()
  let response: Response
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(session ? { Authorization: `${session.tokenType} ${session.accessToken}` } : {}),
        ...options?.headers,
      },
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动')
  }

  let body: ApiResponse<T> | undefined
  const requestId = readRequestId(response)
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError(
      errorMessage('后端返回了无法解析的响应', requestId),
      undefined,
      response.status,
      requestId,
    )
  }

  if (!response.ok || body.code !== 0) {
    if (response.status === 401) clearAuthSession()
    throw new ApiError(
      errorMessage(body.message || '请求失败', requestId),
      body.code,
      response.status,
      requestId,
    )
  }
  return body.data
}
