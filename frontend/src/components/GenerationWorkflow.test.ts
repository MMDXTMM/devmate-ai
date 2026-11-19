import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GenerationWorkspace from './GenerationWorkspace.vue'
import { generationApi } from '../services/generationApi'
import type { GenerationSession } from '../types/generation'

const initial: GenerationSession = { id: '2120000000000000001', originalRequirement: '个人待办工具', status: 'CLARIFYING', latestVersionNo: 1,
  latestSpec: { id: '2120000000000000002', versionNo: 1, requirementSummary: '个人待办', architectureSummary: '技术待选', assumptions: [],
    questions: [{ id: 'g1', category: 'BUSINESS', inputType: 'SINGLE_CHOICE', question: '谁来使用？', reason: '决定规模', aiRecommendation: '个人', recommendationReason: '先小后大', required: true, allowCustomAnswer: true,
      options: [{ id: 'a', label: '个人', description: '个人使用', impact: '本地运行', recommended: true }, { id: 'b', label: '团队', description: '多人使用', impact: '需要共享', recommended: false }] }],
    answers: [], status: 'DRAFT', promptVersion: 'project-builder-v1', stage: 'BUSINESS', readyForGeneration: false, createdAt: '2026-11-13' }, createdAt: '2026-11-13', updatedAt: '2026-11-13' }
enableAutoUnmount(afterEach)
describe('multi-round generation choices', () => {
  beforeEach(() => { vi.spyOn(generationApi, 'list').mockResolvedValue([]); vi.spyOn(generationApi, 'latestRun').mockResolvedValue(null) })
  afterEach(() => vi.restoreAllMocks())
  async function begin() {
    vi.spyOn(generationApi, 'create').mockResolvedValue(structuredClone(initial))
    const wrapper = mount(GenerationWorkspace)
    await wrapper.get('#project-requirement').setValue('个人待办工具')
    await wrapper.get('.generation-entry form').trigger('submit'); await flushPromises()
    return wrapper
  }
  it('shows only new questions and submits a version while preserving prior decisions', async () => {
    vi.spyOn(generationApi, 'clarify').mockResolvedValue({ ...initial, latestVersionNo: 2, latestSpec: { ...initial.latestSpec, id: '2120000000000000003', versionNo: 2, stage: 'LANGUAGE',
      questions: [...initial.latestSpec.questions, { ...initial.latestSpec.questions[0], id: 'g2', question: '选择编程语言', aiRecommendation: 'Python', options: [
        { id: 'python', label: 'Python', description: '适合小工具', impact: '标准库容易开始', recommended: true }, { id: 'typescript', label: 'TypeScript', description: '适合网页', impact: '需要Node', recommended: false }] }],
      answers: [{ questionId: 'g1', decisionMode: 'AI_DEFAULTED', selectedOptionIds: ['a'], answer: '个人使用' }] } })
    const wrapper = await begin()
    await wrapper.get('.recommendation-actions button').trigger('click')
    await wrapper.get('.generation-questions form').trigger('submit'); await flushPromises()
    expect(generationApi.clarify).toHaveBeenCalledWith(initial.id, expect.any(Array), initial.latestSpec.id)
    expect(wrapper.findAll('.requirement-question-card')).toHaveLength(1)
    expect(wrapper.get('.requirement-question-card').text()).toContain('选择编程语言')
    expect(wrapper.get('.generation-decisions').text()).toContain('个人使用')
    expect(wrapper.find('.generation-actions button[type="button"]').exists()).toBe(false)
  })
  it('restores and confirms a ready plan without automatically generating source', async () => {
    const ready: GenerationSession = { ...structuredClone(initial), latestSpec: { ...structuredClone(initial.latestSpec), stage: 'REVIEW', readyForGeneration: true, questions: [] } }
    vi.mocked(generationApi.list).mockResolvedValue([ready]); vi.spyOn(generationApi, 'get').mockResolvedValue(ready)
    vi.spyOn(generationApi, 'confirm').mockResolvedValue({ ...ready, status: 'CONFIRMED' })
    const generate = vi.spyOn(generationApi, 'generate')
    const wrapper = mount(GenerationWorkspace); await flushPromises()
    await wrapper.get('select[aria-label="继续之前的项目"]').setValue(ready.id); await flushPromises()
    expect(wrapper.text()).toContain('选择已齐全')
    await wrapper.get('.generation-confirmed button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('生成项目源码 · 使用 API 额度')
    expect(generate).not.toHaveBeenCalled()
  })
  it('keeps selected answers after failure and blocks repeated submission', async () => {
    let reject!: (value: Error) => void
    const clarify = vi.spyOn(generationApi, 'clarify').mockImplementation(() => new Promise((_, r) => { reject = r }))
    const wrapper = await begin(); await wrapper.get('.recommendation-actions button').trigger('click')
    await wrapper.get('.generation-questions form').trigger('submit'); await wrapper.get('.generation-questions form').trigger('submit')
    expect(clarify).toHaveBeenCalledTimes(1)
    reject(new Error('failed')); await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.get('.generation-questions input').element).toHaveProperty('checked', true)
    expect(wrapper.get('.generation-questions button[type="submit"]').attributes('disabled')).toBeUndefined()
  })
})
