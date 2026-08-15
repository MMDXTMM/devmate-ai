import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GenerationWorkspace from './GenerationWorkspace.vue'
import { generationApi } from '../services/generationApi'
import type { GenerationSession } from '../types/generation'

const draftSession: GenerationSession = {
  id: '2085617802234556417',
  originalRequirement: '做一个企业设备维修工单系统',
  status: 'CLARIFYING',
  latestVersionNo: 1,
  latestSpec: {
    id: '2085617802234556418',
    versionNo: 1,
    requirementSummary: '目标：构建设备维修工单系统。',
    architectureSummary: '采用 Spring Boot 模块化单体。',
    assumptions: ['第一版只生成后端。'],
    questions: [
      { id: 'target-users', question: '有哪些角色？', reason: '决定权限设计', required: true },
      { id: 'core-workflow', question: '核心流程是什么？', reason: '决定状态机', required: true },
      { id: 'business-rules', question: '关键规则是什么？', reason: '决定约束', required: true },
    ],
    answers: [],
    status: 'DRAFT',
    promptVersion: 'guided-requirement-v1',
    createdAt: '2026-08-07T00:00:00Z',
  },
  createdAt: '2026-08-07T00:00:00Z',
  updatedAt: '2026-08-07T00:00:00Z',
}

const structuredSession: GenerationSession = {
  ...structuredClone(draftSession),
  latestSpec: {
    ...structuredClone(draftSession.latestSpec),
    promptVersion: 'guided-requirement-v2',
    questions: [
      {
        id: 'target-users',
        category: 'BUSINESS',
        inputType: 'MULTIPLE_CHOICE',
        question: '第一版需要支持哪些使用角色？',
        reason: '决定权限边界',
        aiRecommendation: '报修人和处理人员',
        recommendationReason: '覆盖核心业务闭环',
        options: [
          { id: 'reporter', label: '报修人', description: '提交工单', impact: '增加个人数据权限', recommended: true },
          { id: 'handler', label: '处理人员', description: '处理工单', impact: '增加处理队列', recommended: true },
        ],
        required: true,
        allowCustomAnswer: true,
      },
      {
        id: 'core-workflow',
        category: 'TRADEOFF',
        inputType: 'SINGLE_CHOICE',
        question: '如何完成工单？',
        reason: '决定状态机',
        aiRecommendation: '报修人确认',
        recommendationReason: '责任边界清晰',
        options: [
          { id: 'reporter-confirm', label: '报修人确认', description: '用户验收', impact: '增加待验收状态', recommended: true },
          { id: 'direct-complete', label: '直接完成', description: '处理后结束', impact: '状态更少', recommended: false },
        ],
        required: true,
        allowCustomAnswer: true,
      },
      {
        id: 'business-rules',
        category: 'TECHNICAL',
        inputType: 'MULTIPLE_CHOICE',
        question: '需要哪些业务保障？',
        reason: '决定数据库约束',
        aiRecommendation: '防重复和状态校验',
        recommendationReason: '保证数据正确',
        options: [
          { id: 'unique', label: '防重复', description: '业务号唯一', impact: '增加唯一键', recommended: true },
          { id: 'state', label: '状态校验', description: '限制跳转', impact: '增加状态机', recommended: true },
        ],
        required: true,
        allowCustomAnswer: true,
      },
      {
        id: 'external-integration',
        category: 'BUSINESS',
        inputType: 'FREE_TEXT',
        question: '需要哪些外部服务？',
        reason: '决定外部适配器',
        aiRecommendation: '第一版暂不接入',
        recommendationReason: '先完成内部闭环',
        options: [],
        required: false,
        allowCustomAnswer: true,
      },
    ],
  },
}

describe('GenerationWorkspace', () => {
  it('creates, clarifies and confirms a generation requirement', async () => {
    vi.spyOn(generationApi, 'create').mockResolvedValue(structuredClone(draftSession))
    vi.spyOn(generationApi, 'clarify').mockResolvedValue({
      ...structuredClone(draftSession),
      latestVersionNo: 2,
      latestSpec: {
        ...structuredClone(draftSession.latestSpec),
        id: '2085617802234556419',
        versionNo: 2,
        answers: [
          { questionId: 'target-users', answer: '员工和维修管理员' },
          { questionId: 'core-workflow', answer: '待受理到已完成' },
          { questionId: 'business-rules', answer: '工单编号不能重复' },
        ],
      },
    })
    vi.spyOn(generationApi, 'confirm').mockResolvedValue({
      ...structuredClone(draftSession),
      status: 'CONFIRMED',
      latestVersionNo: 2,
      confirmedVersionId: '2085617802234556419',
      latestSpec: {
        ...structuredClone(draftSession.latestSpec),
        id: '2085617802234556419',
        versionNo: 2,
        status: 'CONFIRMED',
      },
    })

    const wrapper = mount(GenerationWorkspace)
    await wrapper.get('#project-requirement').setValue('做一个企业设备维修工单系统')
    await wrapper.get('.generation-entry form').trigger('submit')
    await flushPromises()

    expect(generationApi.create).toHaveBeenCalledWith('做一个企业设备维修工单系统')
    expect(wrapper.text()).toContain('反向提问')
    const textareas = wrapper.findAll('.generation-questions textarea')
    await textareas[0].setValue('员工和维修管理员')
    await textareas[1].setValue('待受理到已完成')
    await textareas[2].setValue('工单编号不能重复')
    await wrapper.get('.generation-questions form').trigger('submit')
    await flushPromises()

    expect(generationApi.clarify).toHaveBeenCalledWith(draftSession.id, [
      { questionId: 'target-users', answer: '员工和维修管理员' },
      { questionId: 'core-workflow', answer: '待受理到已完成' },
      { questionId: 'business-rules', answer: '工单编号不能重复' },
    ])
    expect(wrapper.findAll('.generation-questions textarea')[0].element.value)
      .toBe('员工和维修管理员')
    await wrapper.get('.generation-actions .primary').trigger('click')
    await flushPromises()

    expect(generationApi.confirm).toHaveBeenCalledWith(
      draftSession.id,
      '2085617802234556419',
    )
    expect(wrapper.text()).toContain('方案已经确认')
    expect(wrapper.text()).toContain('代码生成 Tool 尚未接入')
  })

  it('does not submit until every required question has an answer', async () => {
    vi.spyOn(generationApi, 'create').mockResolvedValue(structuredClone(draftSession))
    const wrapper = mount(GenerationWorkspace)
    await wrapper.get('#project-requirement').setValue('做一个工单系统')
    await wrapper.get('.generation-entry form').trigger('submit')
    await flushPromises()

    const submit = wrapper.get('.generation-questions button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
  })

  it('renders structured choices and distinguishes accepted recommendation from AI default', async () => {
    vi.spyOn(generationApi, 'create').mockResolvedValue(structuredClone(structuredSession))
    vi.spyOn(generationApi, 'clarify').mockResolvedValue({
      ...structuredClone(structuredSession),
      latestVersionNo: 2,
      latestSpec: {
        ...structuredClone(structuredSession.latestSpec),
        id: '2085617802234556420',
        versionNo: 2,
        answers: [
          {
            questionId: 'target-users',
            decisionMode: 'USER_ACCEPTED_RECOMMENDATION',
            selectedOptionIds: ['reporter', 'handler'],
            customAnswer: '',
            answer: '用户采用 AI 推荐：报修人、处理人员',
          },
          {
            questionId: 'core-workflow',
            decisionMode: 'AI_DEFAULTED',
            selectedOptionIds: ['reporter-confirm'],
            customAnswer: '',
            answer: '由 AI 按推荐方案决定：报修人确认',
          },
          {
            questionId: 'business-rules',
            decisionMode: 'USER_SELECTED',
            selectedOptionIds: ['unique', 'state'],
            customAnswer: '',
            answer: '用户选择：防重复、状态校验',
          },
          {
            questionId: 'external-integration',
            decisionMode: 'CUSTOM',
            selectedOptionIds: [],
            customAnswer: '企业微信通知',
            answer: '自定义：企业微信通知',
          },
        ],
      },
    })

    const wrapper = mount(GenerationWorkspace)
    await wrapper.get('#project-requirement').setValue('做一个设备维修工单系统')
    await wrapper.get('.generation-entry form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('AI 建议：报修人和处理人员')
    expect(wrapper.text()).toContain('代码影响：增加待验收状态')
    const cards = wrapper.findAll('.requirement-question-card')
    await cards[0].findAll('.recommendation-actions button')[0].trigger('click')
    await cards[1].findAll('.recommendation-actions button')[1].trigger('click')
    const ruleInputs = cards[2].findAll('input[type="checkbox"]')
    await ruleInputs[0].setValue(true)
    await ruleInputs[1].setValue(true)
    await cards[3].get('textarea').setValue('企业微信通知')

    const submit = wrapper.get('.generation-actions button[type="submit"]')
    expect(submit.attributes('disabled')).toBeUndefined()
    await wrapper.get('.generation-questions form').trigger('submit')
    await flushPromises()

    expect(generationApi.clarify).toHaveBeenCalledWith(structuredSession.id, [
      {
        questionId: 'target-users',
        decisionMode: 'USER_ACCEPTED_RECOMMENDATION',
        selectedOptionIds: ['reporter', 'handler'],
        customAnswer: '',
      },
      {
        questionId: 'core-workflow',
        decisionMode: 'AI_DEFAULTED',
        selectedOptionIds: ['reporter-confirm'],
        customAnswer: '',
      },
      {
        questionId: 'business-rules',
        decisionMode: 'USER_SELECTED',
        selectedOptionIds: ['unique', 'state'],
        customAnswer: '',
      },
      {
        questionId: 'external-integration',
        decisionMode: 'CUSTOM',
        selectedOptionIds: [],
        customAnswer: '企业微信通知',
      },
    ])
    expect(wrapper.text()).toContain('已采用 AI 推荐')
    expect(wrapper.text()).toContain('由 AI 决定')
  })
})
