import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import RetrievalModal from './RetrievalModal.vue'
import { projectApi } from '../services/projectApi'

describe('RetrievalModal', () => {
  it('requires a query for project-wide retrieval', async () => {
    const wrapper = mount(RetrievalModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('请输入需要检索的代码或工程问题')
  })

  it('renders ranked evidence and budget information', async () => {
    vi.spyOn(projectApi, 'searchRetrieval').mockResolvedValue({
      projectId: '1',
      revision: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      query: 'reserve stock',
      configVersion: 'lexical-graph-v1',
      requestedMode: 'HYBRID',
      executedMode: 'HYBRID',
      embeddingProvider: 'LOCAL',
      embeddingModel: 'code-hash-v1',
      vectorIndexAvailable: true,
      vectorCandidateCount: 2,
      vectorLimitReached: false,
      candidateCount: 6,
      candidateLimitReached: false,
      referenceLimitReached: false,
      topK: 3,
      tokenBudget: 1000,
      usedTokens: 200,
      selectedCount: 1,
      trimmedCount: 1,
      omittedTrimmedDetails: 0,
      hits: [{
        chunkId: '10',
        documentId: '11',
        filePath: 'src/OrderService.java',
        sourceKind: 'SOURCE_CODE',
        chunkType: 'METHOD',
        symbolName: 'OrderService#reserveStock()',
        startLine: 10,
        endLine: 20,
        score: 42,
        estimatedTokens: 200,
        reasons: ['SYMBOL_TERM'],
        excerpt: 'void reserveStock() {}',
      }],
      trimmed: [{
        chunkId: '12',
        filePath: 'src/Other.java',
        symbolName: 'Other#run()',
        estimatedTokens: 100,
        reason: 'TOP_K',
      }],
    })
    const wrapper = mount(RetrievalModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await wrapper.get('textarea').setValue('reserve stock')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('OrderService#reserveStock()')
    expect(wrapper.text()).toContain('200 / 1000（20%）')
    expect(wrapper.text()).toContain('SYMBOL_TERM')
  })
})
