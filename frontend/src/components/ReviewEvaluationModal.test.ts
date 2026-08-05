import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReviewEvaluationModal from './ReviewEvaluationModal.vue'
import { projectApi } from '../services/projectApi'
import type {
  AiReview,
  ReviewDiff,
  ReviewEvaluationCase,
  ReviewEvaluationRun,
} from '../types/project'

describe('ReviewEvaluationModal', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(projectApi, 'latestReviewDiff').mockResolvedValue(diff())
    vi.spyOn(projectApi, 'latestAiReview').mockResolvedValue(aiReview())
    vi.spyOn(projectApi, 'listReviewEvaluationCases').mockResolvedValue([evaluationCase()])
    vi.spyOn(projectApi, 'listReviewEvaluationRuns').mockResolvedValue([
      evaluationRun('AGENT'),
      evaluationRun('FIXED'),
    ])
  })

  it('loads the fixed dataset and compares the newest FIXED and AGENT snapshots without invoking a model', async () => {
    const evaluationSpy = vi.spyOn(projectApi, 'runReviewEvaluation').mockResolvedValue(
      evaluationRun('AGENT'),
    )
    const wrapper = mount(ReviewEvaluationModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('库存丢失更新')
    expect(wrapper.text()).toContain('FIXED')
    expect(wrapper.text()).toContain('AGENT')
    expect(wrapper.text()).toContain('0.800')
    expect(evaluationSpy).not.toHaveBeenCalled()
  })

  it('evaluates only the latest persisted AI task after an explicit click', async () => {
    const evaluationSpy = vi.spyOn(projectApi, 'runReviewEvaluation').mockResolvedValue(
      evaluationRun('AGENT'),
    )
    const wrapper = mount(ReviewEvaluationModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="run-evaluation"]').trigger('click')
    await flushPromises()

    expect(evaluationSpy).toHaveBeenCalledWith('1', 'known-defects-v1', '9')
    expect(wrapper.text()).toContain('AGENT 评测完成')
  })

  it('omits defect-only fields when creating a clean control case', async () => {
    const createSpy = vi.spyOn(projectApi, 'createReviewEvaluationCase').mockResolvedValue({
      ...evaluationCase(),
      expectationType: 'CLEAN',
      category: undefined,
      filePath: undefined,
      startLine: undefined,
      endLine: undefined,
    })
    const wrapper = mount(ReviewEvaluationModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('CLEAN')
    const inputs = wrapper.findAll('input')
    await inputs[1].setValue('clean-control')
    await inputs[2].setValue('无缺陷对照')
    await wrapper.get('textarea').setValue('人工确认本次变更没有目标缺陷')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createSpy).toHaveBeenCalledWith('1', {
      reviewTaskId: '2',
      datasetVersion: 'known-defects-v1',
      caseKey: 'clean-control',
      name: '无缺陷对照',
      expectationType: 'CLEAN',
      category: undefined,
      filePath: undefined,
      startLine: undefined,
      endLine: undefined,
      rationale: '人工确认本次变更没有目标缺陷',
    })
  })
})

function diff(): ReviewDiff {
  return {
    id: '2',
    projectId: '1',
    baseRevision: 'a'.repeat(40),
    targetRevision: 'b'.repeat(40),
    status: 'SUCCEEDED',
    changedFiles: 1,
    fullyMappedFiles: 1,
    partiallyMappedFiles: 0,
    skippedFiles: 0,
    createdAt: '2026-08-05T00:00:00Z',
    files: [],
  }
}

function aiReview(): AiReview {
  return {
    id: '9',
    projectId: '1',
    reviewTaskId: '2',
    staticAnalysisTaskId: '3',
    invocationId: '4',
    revision: 'b'.repeat(40),
    provider: 'TEST',
    modelName: 'test-model',
    promptVersion: 'review-agent-v1',
    executionMode: 'AGENT',
    status: 'SUCCEEDED',
    contextChunks: 2,
    findingCount: 1,
    rejectedFindings: 0,
    promptTokens: 100,
    completionTokens: 50,
    totalTokens: 150,
    latencyMs: 800,
    createdAt: '2026-08-05T00:00:00Z',
    findings: [],
    toolCalls: [],
  }
}

function evaluationCase(): ReviewEvaluationCase {
  return {
    id: '10',
    projectId: '1',
    reviewTaskId: '2',
    datasetVersion: 'known-defects-v1',
    caseKey: 'lost-update',
    name: '库存丢失更新',
    targetRevision: 'b'.repeat(40),
    expectationType: 'DEFECT',
    category: 'CONCURRENCY',
    filePath: 'src/OrderService.java',
    startLine: 20,
    endLine: 32,
    rationale: '并发请求可能同时通过库存检查',
    createdAt: '2026-08-05T00:00:00Z',
    updatedAt: '2026-08-05T00:00:00Z',
  }
}

function evaluationRun(executionMode: 'FIXED' | 'AGENT'): ReviewEvaluationRun {
  return {
    id: executionMode === 'FIXED' ? '20' : '21',
    projectId: '1',
    reviewTaskId: '2',
    aiReviewTaskId: executionMode === 'FIXED' ? '8' : '9',
    datasetVersion: 'known-defects-v1',
    datasetHash: 'hash',
    executionMode,
    revision: 'b'.repeat(40),
    modelName: 'test-model',
    promptVersion: executionMode === 'FIXED' ? 'ai-review-v1' : 'review-agent-v1',
    status: 'SUCCEEDED',
    expectedDefects: 2,
    predictedFindings: 2,
    truePositives: 1,
    falsePositives: 0,
    falseNegatives: 1,
    manualReviewCount: 0,
    partialMetrics: false,
    precision: 1,
    recall: 0.667,
    f1: 0.8,
    totalTokens: executionMode === 'FIXED' ? 100 : 150,
    latencyMs: executionMode === 'FIXED' ? 500 : 800,
    toolCallCount: executionMode === 'FIXED' ? 0 : 2,
    toolSuccessCount: executionMode === 'FIXED' ? 0 : 2,
    createdAt: '2026-08-05T00:00:00Z',
    finishedAt: '2026-08-05T00:00:01Z',
    results: [],
  }
}
