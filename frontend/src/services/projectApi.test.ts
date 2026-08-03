import { describe, expect, it, vi } from 'vitest'
import { ApiError, projectApi } from './projectApi'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('projectApi', () => {
  it('builds pagination and filter query parameters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: { page: 2, size: 10, total: 0, pages: 0, items: [] },
      timestamp: '2026-08-03T00:00:00Z',
    }))

    await projectApi.list({ page: 2, size: 10, name: ' devmate ', status: 'READY' })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects?page=2&size=10&name=devmate&status=READY',
      expect.any(Object),
    )
  })

  it('keeps project ids as strings when creating a project', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: { id: '2084116785588305922', name: 'devmate-ai' },
      timestamp: '2026-08-03T00:00:00Z',
    }, 201))

    const result = await projectApi.create({
      name: 'devmate-ai',
      description: '',
      sourceType: 'GIT',
      sourceLocation: 'https://github.com/MMDXTMM/devmate-ai.git',
      defaultBranch: 'main',
    })

    expect(result.id).toBe('2084116785588305922')
  })

  it('surfaces backend business errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 40000,
      message: 'Git项目必须填写仓库地址',
      timestamp: '2026-08-03T00:00:00Z',
    }, 400))

    await expect(projectApi.create({
      name: 'demo',
      description: '',
      sourceType: 'GIT',
      sourceLocation: '',
      defaultBranch: 'main',
    })).rejects.toEqual(expect.objectContaining<ApiError>({
      message: 'Git项目必须填写仓库地址',
      status: 400,
    }))
  })

  it('explains when the backend cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(projectApi.list({ page: 1, size: 10 })).rejects.toThrow(
      '无法连接后端服务，请确认 Spring Boot 已启动',
    )
  })
})
