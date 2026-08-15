import { describe, expect, it, vi } from 'vitest'
import { runProjectUnderstanding } from './projectUnderstandingWorkflow'

describe('runProjectUnderstanding', () => {
  it('imports source before building the searchable knowledge index', async () => {
    const calls: string[] = []
    const api = {
      importSource: vi.fn(async () => {
        calls.push('import')
        return { totalFiles: 2 }
      }),
      indexEmbeddings: vi.fn(async () => {
        calls.push('embedding')
        return { processedChunks: 3 }
      }),
    }

    const result = await runProjectUnderstanding('project-1', api as never)

    expect(calls).toEqual(['import', 'embedding'])
    expect(result.embeddingIndex.processedChunks).toBe(3)
  })

  it('does not build an index when source import fails', async () => {
    const api = {
      importSource: vi.fn().mockRejectedValue(new Error('import failed')),
      indexEmbeddings: vi.fn(),
    }

    await expect(runProjectUnderstanding('project-1', api as never)).rejects.toThrow('import failed')
    expect(api.indexEmbeddings).not.toHaveBeenCalled()
  })
})
