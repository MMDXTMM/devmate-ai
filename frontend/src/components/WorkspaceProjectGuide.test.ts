import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { modelAccessKey } from '../composables/useModelAccess'
import WorkspaceProjectGuide from './WorkspaceProjectGuide.vue'
import { ApiError, projectApi } from '../services/projectApi'
import { guideFixture } from '../test/fixtures/projectGuide'
import type { Project, ProjectUnderstandingReport } from '../types/project'

const project: Project = {
  id: '1',
  name: '表格助手',
  description: '帮助整理表格',
  sourceType: 'GIT',
  sourceLocation: 'https://github.com/example/demo.git',
  status: 'READY',
  currentRevision: 'abc',
  createdAt: '2026-11-11T00:00:00',
  updatedAt: '2026-11-11T00:00:00',
}
function report(projectId = '1', revision = 'abc'): ProjectUnderstandingReport {
  return {
    id: 'report-' + projectId,
    projectId,
    revision,
    status: 'SUCCEEDED',
    promptVersion: 'project-guide-v2',
    provider: 'COMPATIBLE',
    modelName: 'example',
    businessFlows: [],
    readingGuide: [],
    risksAndUnknowns: [],
    promptTokens: 1,
    completionTokens: 1,
    totalTokens: 2,
    attemptKey: 'test',
    createdAt: '2026-11-11T00:00:00',
    guide: guideFixture(),
  }
}
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(WorkspaceProjectGuide, { props: { project } })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof render>, text: string) {
  return wrapper.findAll('button').find((item) => item.text() === text)!
}
beforeEach(() => {
  vi.spyOn(projectApi, 'latestUnderstandingReport').mockResolvedValue(report())
  vi.spyOn(projectApi, 'createUnderstandingReport').mockResolvedValue(report())
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('WorkspaceProjectGuide', () => {
  it('requests API setup before generating a missing guide and sends no model job when canceled', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockRejectedValue(new ApiError('尚无报告', undefined, 404))
    const guard = vi.fn().mockResolvedValue(false)
    const wrapper = mount(WorkspaceProjectGuide, { props: { project }, global: { provide: { [modelAccessKey as symbol]: guard } } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(guard).toHaveBeenCalledWith('生成项目概要与新人导览')
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
    expect(button(wrapper, '重新分析').attributes('disabled')).toBeUndefined()
  })
  it('restores model-selected icons from the cached report without generating again', async () => {
    const saved = report()
    saved.guide!.content.appearance = { projectIcon: '🔭', modules: [{ moduleId: 'convert', icon: '🌈' }] }
    vi.mocked(projectApi.latestUnderstandingReport).mockResolvedValue(saved)
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('#project-overview-title span').text()).toBe('🔭')
    expect(wrapper.get('.guide-mascot').text()).toBe('🔭')
    expect(wrapper.get('.guide-module-emoji').text()).toBe('🌈')
    expect(wrapper.get('.diagram-module-picker button span').text()).toBe('🌈')
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
  })
  it('opens with the persisted beginner guide, languages, modules and learning value without paid calls', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('项目概要 · 新人导览')
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(wrapper.text()).toContain('Python')
    expect(wrapper.text()).toContain('先检查，再汇总')
    expect(wrapper.text()).toContain('学习如何在处理前验证输入')
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
    await wrapper.get('.guide-highlight-card').trigger('click')
    expect(wrapper.text()).toContain('读取前检查文件')
    expect(wrapper.text()).toContain('阅读输入校验实现')
    expect(wrapper.find('pre').exists()).toBe(false)
    await wrapper.get('.guide-detail-actions button').trigger('click')
    expect(wrapper.text()).toContain('src/main.py')
    await wrapper.get('.guide-detail-actions .primary').trigger('click')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('先检查，再汇总')
  })
  it('generates once on first entry and uses a saved result on the next entry', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockRejectedValueOnce(
      new ApiError('尚无报告', undefined, 404),
    )
    const first = render()
    await flushPromises()
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledTimes(1)
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledWith(
      '1',
      'abc',
      expect.stringMatching(/^[0-9a-f-]{36}$/),
    )
    expect(first.find('.project-guide').exists()).toBe(true)
    first.unmount()
    render()
    await flushPromises()
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledTimes(1)
  })
  it('keeps an old guide visible while filling learning highlights and does not mutate it', async () => {
    const old = report()
    old.guide!.content.overview = undefined
    vi.mocked(projectApi.latestUnderstandingReport).mockResolvedValue(old)
    let finish!: (value: ProjectUnderstandingReport) => void
    vi.mocked(projectApi.createUnderstandingReport).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(wrapper.text()).toContain('分析会使用当前账户的模型额度')
    expect(wrapper.find('.guide-highlights').exists()).toBe(false)
    finish(report())
    await flushPromises()
    expect(wrapper.find('.guide-highlights').exists()).toBe(true)
    expect(old.guide!.content.overview).toBeUndefined()
  })
  it('does not repeatedly pay after a server or saved failure; model configuration remains accessible', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockResolvedValue({
      ...report(),
      status: 'FAILED',
      guide: null,
      errorMessage: '模型未配置',
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('模型未配置')
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
    await button(wrapper, '检查大模型连接').trigger('click')
    expect(wrapper.emitted('configureModel')).toHaveLength(1)
    await button(wrapper, '重试生成').trigger('click')
    await flushPromises()
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledTimes(1)
  })
  it('retains the same attempt key when the response is lost and blocks double submission', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockRejectedValue(
      new ApiError('尚无报告', undefined, 404),
    )
    vi.mocked(projectApi.createUnderstandingReport)
      .mockRejectedValueOnce(new ApiError('网络中断'))
      .mockResolvedValue(report())
    const wrapper = render()
    await flushPromises()
    const key = vi.mocked(projectApi.createUnderstandingReport).mock
      .calls[0]?.[2]
    await button(wrapper, '重试生成').trigger('click')
    await flushPromises()
    expect(
      vi.mocked(projectApi.createUnderstandingReport).mock.calls[1]?.[2],
    ).toBe(key)
  })
  it('accepts a background submission immediately and polls the saved result without resubmitting', async () => {
    vi.useFakeTimers()
    vi.mocked(projectApi.latestUnderstandingReport)
      .mockRejectedValueOnce(new ApiError('尚无报告', undefined, 404))
      .mockResolvedValue(report())
    vi.mocked(projectApi.createUnderstandingReport).mockResolvedValue({ ...report(), status: 'RUNNING', guide: null })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('后台正在阅读项目')
    expect(wrapper.text()).toContain('离开页面也不会取消任务')
    expect(button(wrapper, '重新分析').attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledTimes(1)
  })
  it('polls a running report without generating again and stops polling after completion', async () => {
    vi.useFakeTimers()
    vi.mocked(projectApi.latestUnderstandingReport)
      .mockResolvedValueOnce({ ...report(), status: 'RUNNING', guide: null })
      .mockResolvedValue(report())
    const wrapper = render()
    await flushPromises()
    expect(button(wrapper, '重新分析').attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(wrapper.text()).toContain('帮助财务人员')
    await vi.advanceTimersByTimeAsync(6000)
    expect(projectApi.latestUnderstandingReport).toHaveBeenCalledTimes(2)
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
  })
  it('prevents another model call while generation is pending and stops bounded polling on exit', async () => {
    vi.useFakeTimers()
    vi.mocked(projectApi.latestUnderstandingReport).mockRejectedValueOnce(
      new ApiError('尚无报告', undefined, 404),
    )
    let finish!: (value: ProjectUnderstandingReport) => void
    vi.mocked(projectApi.createUnderstandingReport).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = render()
    await flushPromises()
    expect(button(wrapper, '正在整理…').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '刷新导览').attributes('disabled')).toBeDefined()
    await button(wrapper, '正在整理…').trigger('click')
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledTimes(1)
    finish({ ...report(), status: 'RUNNING', guide: null })
    await flushPromises()
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10000)
    expect(projectApi.latestUnderstandingReport).toHaveBeenCalledTimes(1)
  })
  it('stops automatic polling at its budget instead of waiting forever', async () => {
    vi.useFakeTimers()
    vi.mocked(projectApi.latestUnderstandingReport).mockResolvedValue({
      ...report(),
      status: 'RUNNING',
      guide: null,
    })
    const wrapper = render()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(663000)
    await flushPromises()
    expect(wrapper.text()).toContain('无需重复生成')
    const requests = vi.mocked(projectApi.latestUnderstandingReport).mock.calls
      .length
    await vi.advanceTimersByTimeAsync(10000)
    expect(projectApi.latestUnderstandingReport).toHaveBeenCalledTimes(requests)
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
  })

  it('does not leak an old project result when switching projects during generation', async () => {
    let finish!: (value: ProjectUnderstandingReport) => void
    vi.mocked(projectApi.latestUnderstandingReport)
      .mockRejectedValueOnce(new ApiError('尚无报告', undefined, 404))
      .mockResolvedValue(report('2', 'new'))
    vi.mocked(projectApi.createUnderstandingReport).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper.setProps({
      project: { ...project, id: '2', currentRevision: 'new' },
    })
    await flushPromises()
    finish({
      ...report(),
      guide: {
        ...guideFixture(),
        content: {
          ...guideFixture().content,
          project: {
            ...guideFixture().content.project,
            purpose: '不该显示的旧项目',
          },
        },
      },
    })
    await flushPromises()
    expect(wrapper.text()).not.toContain('不该显示的旧项目')
    expect(wrapper.text()).toContain('帮助财务人员')
  })
  it('does not show stale revisions and generates a guide for the current snapshot', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockResolvedValue(
      report('1', 'old'),
    )
    render()
    await flushPromises()
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledWith(
      '1',
      'abc',
      expect.any(String),
    )
  })
  it('does not generate on forbidden reads or before source import', async () => {
    vi.mocked(projectApi.latestUnderstandingReport).mockRejectedValue(
      new ApiError('没有权限', undefined, 403),
    )
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('没有权限')
    expect(projectApi.createUnderstandingReport).not.toHaveBeenCalled()
    await wrapper.setProps({
      project: { ...project, status: 'CREATED', currentRevision: undefined },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('请先完成项目导入')
    expect(projectApi.latestUnderstandingReport).toHaveBeenCalledTimes(1)
  })
})
