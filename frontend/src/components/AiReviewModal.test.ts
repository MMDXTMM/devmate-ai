import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AiReviewModal from './AiReviewModal.vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { AiReview, ReviewDiff } from '../types/project'

describe('AiReviewModal', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('does not spend model quota until the user explicitly starts a review', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    const latestDiffSpy = vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff())
    const createSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    expect(createSpy).not.toHaveBeenCalled()
    expect(latestDiffSpy).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('还没有 AI 审查记录')

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(latestDiffSpy).toHaveBeenCalledWith('2084116785588305922')
    expect(createSpy).toHaveBeenCalledWith('2084116785588305922', {
      reviewTaskId: '2084116785588308000',
      revision: 'a'.repeat(40),
      attemptKey: expect.stringMatching(/^[0-9a-f-]{36}$/),
    })
    expect(wrapper.text()).toContain('库存检查与扣减不是原子操作')
  })

  it('runs agent mode only after an explicit click and renders its audited tool chain', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff())
    vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    expect(agentSpy).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="agent-review"]').trigger('click')
    await flushPromises()

    expect(agentSpy).toHaveBeenCalledWith('2084116785588305922', {
      reviewTaskId: '2084116785588308000',
      revision: 'a'.repeat(40),
      attemptKey: expect.stringMatching(/^[0-9a-f-]{36}$/),
    })
    expect(wrapper.text()).toContain('Agent 工具调用链')
    expect(wrapper.text()).toContain('searchCode')
    expect(wrapper.text()).toContain('hits=1')
  })

  it('ignores an old latest-review response after switching projects', async () => {
    const oldLatest = deferred<AiReview>()
    vi.spyOn(projectApi, 'latestAiReview')
      .mockReturnValueOnce(oldLatest.promise)
      .mockResolvedValueOnce(report({
        projectId: '2084116785588305999',
        modelName: 'project-b-model',
      }))
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'project-a' },
    })

    await wrapper.setProps({ open: false, projectId: undefined, projectName: undefined })
    await wrapper.setProps({
      open: true,
      projectId: '2084116785588305999',
      projectName: 'project-b',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('project-b-model')
    oldLatest.resolve(report({ modelName: 'stale-project-a-model' }))
    await flushPromises()

    expect(wrapper.text()).toContain('project-b-model')
    expect(wrapper.text()).not.toContain('stale-project-a-model')
  })

  it('ignores an old completed-review response after switching projects', async () => {
    const oldReview = deferred<AiReview>()
    vi.spyOn(projectApi, 'latestAiReview')
      .mockRejectedValueOnce(new ApiError('暂无记录', 40400, 404))
      .mockResolvedValueOnce(report({
        projectId: '2084116785588305999',
        modelName: 'project-b-model',
    }))
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff())
    const createSpy = vi.spyOn(projectApi, 'createAiReview').mockReturnValue(oldReview.promise)
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'project-a' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()
    expect(createSpy).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ open: false, projectId: undefined, projectName: undefined })
    await wrapper.setProps({
      open: true,
      projectId: '2084116785588305999',
      projectName: 'project-b',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('project-b-model')
    oldReview.resolve(report({ modelName: 'stale-project-a-model' }))
    await flushPromises()

    expect(wrapper.text()).toContain('project-b-model')
    expect(wrapper.text()).not.toContain('stale-project-a-model')
  })

  it('does not create a review when the project changes while loading the Diff', async () => {
    const pendingDiff = deferred<ReviewDiff>()
    vi.spyOn(projectApi, 'latestAiReview')
      .mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    const latestDiffSpy = vi.spyOn(projectApi, 'latestReviewDiff').mockReturnValue(pendingDiff.promise)
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'project-a' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    expect(latestDiffSpy).toHaveBeenCalledWith('2084116785588305922')
    await wrapper.setProps({ open: false, projectId: undefined, projectName: undefined })
    await wrapper.setProps({
      open: true,
      projectId: '2084116785588305999',
      projectName: 'project-b',
    })
    pendingDiff.resolve(reviewDiff())
    await flushPromises()

    expect(fixedSpy).not.toHaveBeenCalled()
    expect(agentSpy).not.toHaveBeenCalled()
  })

  it('ignores repeated review triggers while the first request is pending', async () => {
    const pendingDiff = deferred<ReviewDiff>()
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    const latestDiffSpy = vi.spyOn(projectApi, 'latestReviewDiff').mockReturnValue(pendingDiff.promise)
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    const fixedButton = wrapper.get('[data-testid="fixed-review"]')
    await Promise.all([
      fixedButton.trigger('click'),
      fixedButton.trigger('click'),
    ])

    expect(latestDiffSpy).toHaveBeenCalledTimes(1)
    pendingDiff.resolve(reviewDiff())
    await flushPromises()

    expect(fixedSpy).toHaveBeenCalledTimes(1)
    expect(agentSpy).not.toHaveBeenCalled()
  })

  it('does not create a review when the latest Diff has not succeeded', async () => {
    const latestReviewSpy = vi.spyOn(projectApi, 'latestAiReview')
      .mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff({
      status: 'FAILED',
      errorMessage: 'Diff 生成失败',
    }))
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(fixedSpy).not.toHaveBeenCalled()
    expect(agentSpy).not.toHaveBeenCalled()
    expect(latestReviewSpy).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="alert"]').text()).toContain('最近一次 Diff 尚未成功')
  })

  it('does not create a review when the successful Diff has no target revision', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff({
      targetRevision: undefined,
    }))
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(fixedSpy).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('最近一次 Diff 缺少目标版本')
  })

  it('does not create a review when loading the latest Diff fails', async () => {
    vi.spyOn(projectApi, 'latestAiReview').mockRejectedValue(new ApiError('暂无记录', 40400, 404))
    vi.spyOn(projectApi, 'latestReviewDiff').mockRejectedValue(
      new ApiError('读取最近一次 Diff 失败', 50000, 500),
    )
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockResolvedValue(report())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(fixedSpy).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('读取最近一次 Diff 失败')
  })

  it('reloads once without retrying when the backend rejects a drifted Diff', async () => {
    const latestReviewSpy = vi.spyOn(projectApi, 'latestAiReview')
      .mockRejectedValueOnce(new ApiError('暂无记录', 40400, 404))
      .mockResolvedValueOnce(report())
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(reviewDiff())
    const fixedSpy = vi.spyOn(projectApi, 'createAiReview').mockRejectedValue(
      new ApiError('Diff已发生变化，请刷新后重试', 40900, 409),
    )
    const agentSpy = vi.spyOn(projectApi, 'createAgentAiReview').mockResolvedValue(agentReport())
    const wrapper = mount(AiReviewModal, {
      props: { open: true, projectId: '2084116785588305922', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="fixed-review"]').trigger('click')
    await flushPromises()

    expect(fixedSpy).toHaveBeenCalledTimes(1)
    expect(agentSpy).not.toHaveBeenCalled()
    expect(latestReviewSpy).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="alert"]').text()).toContain('Diff已发生变化，请刷新后重试')
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

function reviewDiff(overrides: Partial<ReviewDiff> = {}): ReviewDiff {
  return {
    id: '2084116785588308000',
    projectId: '2084116785588305922',
    baseRevision: '0'.repeat(40),
    targetRevision: 'a'.repeat(40),
    status: 'SUCCEEDED',
    changedFiles: 1,
    fullyMappedFiles: 1,
    partiallyMappedFiles: 0,
    skippedFiles: 0,
    createdAt: '2026-08-04T00:00:00Z',
    finishedAt: '2026-08-04T00:00:01Z',
    files: [],
    ...overrides,
  }
}

function report(overrides: Partial<AiReview> = {}): AiReview {
  return {
    id: '100',
    projectId: '1',
    reviewTaskId: '2',
    staticAnalysisTaskId: '3',
    invocationId: '4',
    attemptKey: '123e4567-e89b-42d3-a456-426614174000',
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
    ...overrides,
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

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
