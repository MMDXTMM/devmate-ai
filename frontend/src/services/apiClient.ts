import type { ApiResponse } from '../types/project'
import { clearAuthSession, getAuthSession } from './authSession'

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
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError('后端返回了无法解析的响应', undefined, response.status)
  }

  if (!response.ok || body.code !== 0) {
    if (response.status === 401) clearAuthSession()
    throw new ApiError(body.message || '请求失败', body.code, response.status)
  }
  return body.data
}
