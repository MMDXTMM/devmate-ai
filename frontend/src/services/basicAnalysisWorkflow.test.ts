import { describe, expect, it, vi } from 'vitest'
import { runBasicAnalysis } from './basicAnalysisWorkflow'

describe('runBasicAnalysis', () => {
  it('runs deterministic analysis stages in dependency order', async () => {
    const calls: string[] = []
    const api = {
      importSource: vi.fn(async () => {
        calls.push('import')
        return { totalFiles: 2 }
      }),
      createReviewDiff: vi.fn(async () => {
        calls.push('diff')
        return { changedFiles: 1 }
      }),
      createStaticAnalysis: vi.fn(async () => {
        calls.push('static')
        return { findingCount: 1 }
      }),
      indexEmbeddings: vi.fn(async () => {
        calls.push('embedding')
        return { processedChunks: 3 }
      }),
    }

    const result = await runBasicAnalysis('project-1', api as never)

    expect(calls).toEqual(['import', 'diff', 'static', 'embedding'])
    expect(result.staticAnalysis.findingCount).toBe(1)
  })

  it('stops after the first failed stage', async () => {
    const api = {
      importSource: vi.fn().mockResolvedValue({}),
      createReviewDiff: vi.fn().mockRejectedValue(new Error('diff failed')),
      createStaticAnalysis: vi.fn(),
      indexEmbeddings: vi.fn(),
    }

    await expect(runBasicAnalysis('project-1', api as never)).rejects.toThrow('diff failed')
    expect(api.createStaticAnalysis).not.toHaveBeenCalled()
    expect(api.indexEmbeddings).not.toHaveBeenCalled()
  })
})
