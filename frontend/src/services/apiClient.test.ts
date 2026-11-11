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

  it('explains that the backend is unavailable when the development proxy returns 502', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('Bad Gateway', {
      status: 502,
      headers: { 'X-Request-Id': 'gateway-failure-9' },
    }))

    await expect(apiRequest('/api/projects/1')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        message: '后端服务暂不可用，请确认 Spring Boot 已启动后重试（请求ID：gateway-failure-9）',
        status: 502,
        requestId: 'gateway-failure-9',
      }),
    )
  })

  it('keeps the HTTP status when a non-gateway response is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>failure</html>', {
      status: 500,
    }))

    await expect(apiRequest('/api/projects/1')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        message: '后端返回了无法解析的响应（HTTP 500）',
        status: 500,
      }),
    )
  })
})
