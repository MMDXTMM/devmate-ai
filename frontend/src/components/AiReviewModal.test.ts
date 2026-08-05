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
    vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(createSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('还没有 AI 审查记录')

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(createSpy).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('库存检查与扣减不是原子操作')
  })

  it('runs agent mode only after an explicit click and renders its audited tool chain', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(agentSpy).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="agent-review"]').trigger('click')
    await flushPromises()

    expect(agentSpy).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('Agent 工具调用链')
    expect(wrapper.text()).toContain('searchCode')
    expect(wrapper.text()).toContain('hits=1')
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

  it('does not present a failed review as a clean result', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockResolvedValue({
      ...report(),
      status: 'FAILED',
      errorMessage: 'Agent未获得可验证的代码检索证据',
      findings: [],
      findingCount: 0,
    })
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Agent未获得可验证的代码检索证据')
    expect(wrapper.text()).not.toContain('本次没有通过证据校验的语义风险')
  })

  it('saves a false-positive decision and updates the finding without rerunning the model', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockResolvedValue(report())
    const feedbackSpy = vi.spyOn(projectApi, 'upsertReviewFeedback').mockResolvedValue({
      id: '9',
      projectId: '1',
      findingId: '5',
      feedbackType: 'FALSE_POSITIVE',
      comment: '调用方已经持有互斥锁',
      createdAt: '2026-08-05T00:00:00Z',
      updatedAt: '2026-08-05T00:00:00Z',
    })
    const createSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="feedback-comment-5"]').setValue(' 调用方已经持有互斥锁 ')
    await wrapper.get('[data-testid="feedback-FALSE_POSITIVE-5"]').trigger('click')
    await flushPromises()

    expect(feedbackSpy).toHaveBeenCalledWith('1', '5', {
      feedbackType: 'FALSE_POSITIVE',
      comment: '调用方已经持有互斥锁',
    })
    expect(createSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前：误报')
  })

  it('shows a readable error when feedback persistence fails', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockResolvedValue(report())
    vi.spyOn(projectApi, 'upsertReviewFeedback').mockRejectedValue(
      new ApiError('审查结论不存在', 40400, 404),
    )
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="feedback-ACCEPTED-5"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('审查结论不存在')
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
    executionMode: 'FIXED',
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
    toolCalls: [],
  }
}

function agentReport(): AiReview {
  return {
    ...report(),
    promptVersion: 'review-agent-v1',
    executionMode: 'AGENT',
    toolCalls: [{
      id: '8',
      toolCallId: 'call-search-1',
      stepNo: 1,
      toolName: 'searchCode',
      argumentsSummary: 'keys=query;characters=24',
      resultSummary: 'mode=LEXICAL_FALLBACK;hits=1;tokens=30',
      status: 'SUCCEEDED',
      latencyMs: 18,
      createdAt: '2026-08-05T00:00:00Z',
    }],
  }
}
