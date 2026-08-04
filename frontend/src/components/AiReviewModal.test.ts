import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AiReviewModal from './AiReviewModal.vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { AiReview } from '../types/project'

describe('AiReviewModal', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('does not spend model quota until the user explicitly starts a review', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    const createSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(createSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('还没有 AI 审查记录')

    await wrapper.get('.ai-review-intro button').trigger('click')
    await flushPromises()

    expect(createSpy).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('库存检查与扣减不是原子操作')
  })

  it('renders evidence, conclusion type and verification plan', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('推断')
    expect(wrapper.text()).toContain('Chunk 42')
    expect(wrapper.text()).toContain('并发测试并校验最终库存')
    expect(wrapper.text()).toContain('150 / 800 ms')
  })
})

function report(): AiReview {
  return {
    id: '100',
    projectId: '1',
    reviewTaskId: '2',
    staticAnalysisTaskId: '3',
    invocationId: '4',
    revision: 'a'.repeat(40),
    provider: 'TEST',
    modelName: 'test-model',
    promptVersion: 'ai-review-v1',
    retrievalConfigVersion: 'lexical-graph-v1',
    retrievalMode: 'LEXICAL_FALLBACK',
    status: 'SUCCEEDED',
    contextChunks: 1,
    findingCount: 1,
    rejectedFindings: 0,
    promptTokens: 100,
    completionTokens: 50,
    totalTokens: 150,
    latencyMs: 800,
    createdAt: '2026-08-04T00:00:00Z',
    findings: [{
      id: '5',
      chunkId: '42',
      source: 'LLM',
      category: 'CONCURRENCY',
      severity: 'HIGH',
      conclusionType: 'INFERENCE',
      confidence: 0.82,
      filePath: 'src/OrderService.java',
      startLine: 20,
      endLine: 32,
      title: '库存检查与扣减不是原子操作',
      evidence: '先检查再扣减',
      riskScenario: '两个请求同时通过检查',
      suggestion: '使用原子条件更新',
      verification: '并发测试并校验最终库存',
    }],
  }
}
