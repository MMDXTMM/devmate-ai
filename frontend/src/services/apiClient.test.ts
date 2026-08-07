import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest } from './apiClient'

describe('apiClient request correlation', () => {
  it('surfaces a safe backend request id on business errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 40900,
      message: '资源状态冲突',
      timestamp: '2026-08-07T00:00:00Z',
    }), {
      status: 409,
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': 'request-abc_123',
      },
    }))

    await expect(apiRequest('/api/projects/1')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        message: '资源状态冲突（请求ID：request-abc_123）',
        code: 40900,
        status: 409,
        requestId: 'request-abc_123',
      }),
    )
  })

  it('does not display an unsafe request id returned by an intermediary', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 50000,
      message: '系统内部错误',
    }), {
      status: 500,
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': 'unsafe request id',
      },
    }))

    await expect(apiRequest('/api/projects/1')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        message: '系统内部错误',
        requestId: undefined,
      }),
    )
  })

  it('keeps the request id when the backend response is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('Bad Gateway', {
      status: 502,
      headers: { 'X-Request-Id': 'gateway-failure-9' },
    }))

    await expect(apiRequest('/api/projects/1')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        message: '后端返回了无法解析的响应（请求ID：gateway-failure-9）',
        status: 502,
        requestId: 'gateway-failure-9',
      }),
    )
  })
})
