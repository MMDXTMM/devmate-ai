import { describe, expect, it, vi } from 'vitest'
import { generationApi } from './generationApi'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify({ code: 0, message: 'success', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('generationApi', () => {
  it('keeps generation ids as strings and sends the one-sentence requirement', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      id: '2085617802234556417',
      latestSpec: { id: '2085617802234556418' },
    }, 201))

    const result = await generationApi.create('做一个库存管理系统')

    expect(result.id).toBe('2085617802234556417')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/generation-sessions',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ requirement: '做一个库存管理系统' }),
      }),
    )
  })

  it('confirms a fixed requirement version', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      id: '10', status: 'CONFIRMED', latestSpec: { id: '11' },
    }))

    await generationApi.confirm('10', '11')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/generation-sessions/10/confirmations',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ versionId: '11' }) }),
    )
  })

  it('sends structured clarification decisions without converting bigint ids', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      id: '2085617802234556417', latestSpec: { id: '2085617802234556419' },
    }))
    const answers = [
      {
        questionId: 'core-workflow',
        decisionMode: 'AI_DEFAULTED' as const,
        selectedOptionIds: ['reporter-confirm'],
        customAnswer: '',
      },
    ]

    await generationApi.clarify('2085617802234556417', answers)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/generation-sessions/2085617802234556417/clarifications',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ answers }) }),
    )
  })
})
