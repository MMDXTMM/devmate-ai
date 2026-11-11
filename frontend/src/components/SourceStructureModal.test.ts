import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SourceStructureModal from './SourceStructureModal.vue'
import { ApiError, projectApi } from '../services/projectApi'
import { guideFixture } from '../test/fixtures/projectGuide'
import type { ProjectUnderstandingReport } from '../types/project'

describe('SourceStructureModal', () => {
  afterEach(() => vi.restoreAllMocks())

  function mockNoReport() { vi.spyOn(projectApi, 'latestUnderstandingReport').mockRejectedValue(new ApiError('暂无报告', undefined, 404)) }

  it('opens with an evidence-backed onboarding guide and can drill into implementation code', async () => {
    mockNoReport()
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue({
      revision: 'abcdef',
      analysisMode: 'STATIC_CODE_EVIDENCE_V2',
      summary: '识别到项目管理模块。',
      moduleCount: 1,
      endpointCount: 1,
      onboarding: {
        purpose: '帮助团队管理项目和代码审查任务。',
        architectureSummary: '请求从 Controller 进入，经 Service 编排后访问数据库。',
        detectedCapabilities: ['Spring Web REST 接口', '数据库持久化'],
        coreJourneys: [{
          moduleId: '10',
          name: '项目管理',
          goal: '负责创建和查询项目。',
          apiEntries: ['POST /api/projects · 创建项目'],
          implementationFlow: ['接口入口：ProjectController.create()', '业务服务：ProjectService.create()'],
          dataOperations: ['insert：projectMapper.insert()'],
          failureSignals: ['存在显式异常分支。'],
          evidenceFiles: ['src/ProjectController.java'],
        }],
        stateModels: [{
          chunkId: '20', name: 'ProjectStatus', values: ['CREATED', 'READY', 'FAILED'],
          filePath: 'src/ProjectStatus.java', startLine: 1, endLine: 5,
        }],
        dataAssets: [{
          chunkId: '21', name: 'project', filePath: 'db/V1.sql', startLine: 1, endLine: 8,
        }],
        readingOrder: [{
          order: 1, category: '业务入口', title: 'ProjectController',
          reason: '先理解项目接口。', filePath: 'src/ProjectController.java',
          symbolName: 'com.example.ProjectController', startLine: 10,
        }],
        unknowns: ['动态调用仍需人工确认。'],
      },
      limitations: ['调用链来自静态推断。'],
      modules: [{
        id: '10',
        name: '项目管理',
        description: '项目管理包含 1 个接口入口。',
        controllerSymbol: 'com.example.ProjectController',
        controllerFilePath: 'src/ProjectController.java',
        startLine: 10,
        endLine: 30,
        features: [{
          id: '11',
          name: '创建或执行项目管理',
          description: '创建项目',
          httpMethods: ['POST'],
          path: '/api/projects',
          controllerSymbol: 'com.example.ProjectController#create()',
          controllerFilePath: 'src/ProjectController.java',
          startLine: 15,
          endLine: 18,
          implementationSteps: 2,
          accessesData: true,
        }],
      }],
    })
    vi.spyOn(projectApi, 'getBusinessFeatureDetail').mockResolvedValue({
      feature: {
        id: '11', name: '创建或执行项目管理', description: '创建项目',
        httpMethods: ['POST'], path: '/api/projects',
        controllerSymbol: 'com.example.ProjectController#create()',
        controllerFilePath: 'src/ProjectController.java', startLine: 15, endLine: 18,
        implementationSteps: 2, accessesData: true,
      },
      flowSummary: 'create() → save()',
      dataOperations: ['insert：projectMapper.insert()'],
      implementation: [{
        chunkId: '11', documentId: '10', layer: 'CONTROLLER',
        symbolName: 'com.example.ProjectController#create()',
        filePath: 'src/ProjectController.java', startLine: 15, endLine: 18,
        explanation: '接口入口。', code: '@PostMapping\nvoid create() {}',
        truncated: false, originalCharacters: 30,
      }],
    })
    const longExecutiveSummary = '项目围绕代码审查任务形成完整业务闭环。'.repeat(12)
    const understandingReport: ProjectUnderstandingReport = {
      id: '30', projectId: '1', revision: 'abcdef', provider: 'DEEPSEEK',
      modelName: 'deepseek-v4-flash', promptVersion: 'project-understanding-v1',
      status: 'SUCCEEDED',
      executiveSummary: longExecutiveSummary,
      architectureNarrative: '请求经过 Controller、Service 与 Mapper 完成状态持久化。',
      businessFlows: [{
        name: '创建项目', goal: '为后续代码导入建立项目容器。',
        steps: ['接收项目资料', '保存项目'], apiEntries: ['POST /api/projects'],
        dataChanges: ['新增 project 记录'], evidence: [{
          chunkId: '11', symbolName: 'com.example.ProjectController#create()',
          filePath: 'src/ProjectController.java', startLine: 15, endLine: 18,
          code: '@PostMapping\nvoid create() {}', truncated: false,
        }],
      }],
      readingGuide: [{ order: 1, title: '项目入口', reason: '先理解项目创建流程。', evidence: [] }],
      risksAndUnknowns: ['动态调用需要运行确认。'], promptTokens: 100,
      completionTokens: 50, totalTokens: 150, latencyMs: 900,
      attemptKey: '123e4567-e89b-42d3-a456-426614174000', createdAt: '2026-08-16T12:00:00',
      finishedAt: '2026-08-16T12:00:01',
    }
    vi.spyOn(projectApi, 'createUnderstandingReport')
      .mockResolvedValueOnce(understandingReport)
      .mockResolvedValueOnce({ ...understandingReport, id: '31' })

    const wrapper = mount(SourceStructureModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('新人导览')
    expect(wrapper.text()).toContain('帮助团队管理项目和代码审查任务')
    expect(wrapper.text()).toContain('用户能完成哪些事情')
    expect(wrapper.text()).toContain('项目管理')
    expect(wrapper.text()).toContain('POST /api/projects')
    expect(wrapper.text()).toContain('CREATED、READY、FAILED')
    expect(wrapper.text()).toContain('推荐阅读顺序')

    await wrapper.get('.ai-understanding-actions .primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('项目围绕代码审查任务形成完整业务闭环')
    expect(wrapper.text()).toContain('DEEPSEEK / deepseek-v4-flash')
    expect(wrapper.get('.ai-report-summary-text').classes()).toContain('collapsed')
    expect(wrapper.get('.ai-report-summary-text').text())
      .toBe(`${longExecutiveSummary.slice(0, 180)}…`)
    expect(wrapper.get('.ai-report-summary-text').text()).not.toBe(longExecutiveSummary)
    expect(wrapper.get('.ai-report-summary-toggle').attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.ai-architecture-details').attributes('open')).toBeUndefined()
    expect(wrapper.get('.ai-flow-card').attributes('open')).toBeUndefined()

    await wrapper.get('.ai-report-summary-toggle').trigger('click')
    expect(wrapper.get('.ai-report-summary-text').classes()).not.toContain('collapsed')
    expect(wrapper.get('.ai-report-summary-text').text()).toBe(longExecutiveSummary)
    expect(wrapper.get('.ai-report-summary-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.ai-report-summary-toggle').text()).toBe('收起摘要')

    await wrapper.get('.ai-flow-card > summary').trigger('click')
    expect(wrapper.get('.ai-flow-card').attributes('open')).toBe('')
    expect(wrapper.get('.ai-flow-card pre code').text()).toContain('@PostMapping')
    await wrapper.get('.ask-business-flow').trigger('click')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('创建项目')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('请引用代码证据')
    await wrapper.get('.ai-architecture-details > summary').trigger('click')
    expect(wrapper.get('.ai-architecture-details').attributes('open')).toBe('')
    expect(projectApi.createUnderstandingReport).toHaveBeenCalledWith('1', 'abcdef', expect.any(String))

    await wrapper.get('.ai-understanding-actions .primary').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ai-report-summary-toggle').attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.ai-architecture-details').attributes('open')).toBeUndefined()
    expect(wrapper.get('.ai-flow-card').attributes('open')).toBeUndefined()

    await wrapper.get('.source-view-tabs button:nth-child(2)').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('总体调用逻辑')
    expect(wrapper.text()).toContain('create() → save()')
    expect(wrapper.get('.business-evidence-list pre code').text()).toContain('@PostMapping')
    expect(projectApi.getBusinessFeatureDetail).toHaveBeenCalledWith('1', '11')
  })

  it('shows the selected symbol as a real code block with Chinese guidance', async () => {
    mockNoReport()
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue({
      revision: 'abcdef', analysisMode: 'STATIC_CODE_EVIDENCE_V2', summary: '',
      moduleCount: 0, endpointCount: 0, modules: [], limitations: [],
      onboarding: {
        purpose: 'demo', architectureSummary: '', detectedCapabilities: [], coreJourneys: [],
        stateModels: [], dataAssets: [], readingOrder: [], unknowns: [],
      },
    })
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([{
      id: '10',
      fileName: 'OrderService.java',
      filePath: 'src/main/java/com/example/OrderService.java',
      sourceKind: 'SOURCE_CODE',
      fileType: 'JAVA',
      packageName: 'com.example',
      revision: 'abcdef',
      structureVersion: 'source-structure-v2',
      status: 'PARSED',
      chunkCount: 1,
    }])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceSymbols').mockResolvedValue([{
      id: '11',
      documentId: '10',
      chunkType: 'METHOD',
      symbolName: 'com.example.OrderService#createOrder()',
      annotations: ['Transactional'],
      startLine: 20,
      endLine: 28,
      contentHash: 'hash',
      revision: 'abcdef',
    }])
    vi.spyOn(projectApi, 'getSourceSymbolDetail').mockResolvedValue({
      id: '11',
      documentId: '10',
      chunkType: 'METHOD',
      symbolName: 'com.example.OrderService#createOrder()',
      annotations: ['Transactional'],
      startLine: 20,
      endLine: 28,
      revision: 'abcdef',
      code: 'public void createOrder() {\n    repository.save(order);\n}',
      truncated: false,
      originalCharacters: 58,
    })

    const wrapper = mount(SourceStructureModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()
    await wrapper.get('.source-view-tabs button:nth-child(3)').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('方法实现')
    expect(wrapper.text()).toContain('代码解读')
    expect(wrapper.get('pre code').text()).toContain('repository.save(order)')
    expect(projectApi.getSourceSymbolDetail).toHaveBeenCalledWith('1', '10', '11')
  })
  const emptyMap = (revision = 'abc') => ({
    revision, analysisMode: 'STATIC_CODE_EVIDENCE_V2', summary: '', moduleCount: 0, endpointCount: 0, modules: [], limitations: [],
    onboarding: { purpose: '业务线索', architectureSummary: '', detectedCapabilities: [], coreJourneys: [], stateModels: [], dataAssets: [], readingOrder: [], unknowns: [] },
  })
  const savedReport = (projectId: string, revision = 'abc'): ProjectUnderstandingReport => ({
    id: `report-${projectId}`, projectId, revision, provider: 'DEEPSEEK', modelName: 'test', promptVersion: 'v2',
    status: 'SUCCEEDED', executiveSummary: `项目${projectId}专属报告`, architectureNarrative: '', businessFlows: [], readingGuide: [],
    risksAndUnknowns: [], attemptKey: 'test', createdAt: '2026-11-11T00:00:00Z',
  })

  it('restores the new guide as a unified beginner view and hides the competing static report', async () => {
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue(emptyMap())
    vi.spyOn(projectApi, 'latestUnderstandingReport').mockResolvedValue({ ...savedReport('1'), guide: guideFixture() })
    const create = vi.spyOn(projectApi, 'createUnderstandingReport')
    const wrapper = mount(SourceStructureModal, { props: { open: true, projectId: '1' } })
    await flushPromises()
    expect(wrapper.find('.project-guide').exists()).toBe(true)
    expect(wrapper.find('.legacy-project-understanding').exists()).toBe(false)
    expect(wrapper.find('.onboarding-hero').exists()).toBe(false)
    expect(wrapper.find('pre').exists()).toBe(false)
    expect(create).not.toHaveBeenCalled()
    await wrapper.get('.guide-module-card').trigger('click')
    expect(wrapper.text()).toContain('文件不存在就返回错误')
    await wrapper.get('.guide-detail-actions .primary').trigger('click')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('整理记录')
  })

  it('automatically reads saved reports without paid generation and rejects outdated revisions', async () => {
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue(emptyMap())
    vi.spyOn(projectApi, 'latestUnderstandingReport').mockResolvedValue(savedReport('1', 'old'))
    const create = vi.spyOn(projectApi, 'createUnderstandingReport')
    const wrapper = mount(SourceStructureModal, { props: { open: true, projectId: '1' } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('项目1专属报告')
    expect(wrapper.get('[role="alert"]').text()).toContain('其他源码版本')
    expect(create).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('ignores a late report after switching projects and clears it on close', async () => {
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue(emptyMap())
    let finish!: (report: ProjectUnderstandingReport) => void
    vi.spyOn(projectApi, 'latestUnderstandingReport')
      .mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockResolvedValue(savedReport('2'))
    const wrapper = mount(SourceStructureModal, { props: { open: true, projectId: '1' } })
    await flushPromises()
    await wrapper.setProps({ projectId: '2' })
    await flushPromises()
    expect(wrapper.text()).toContain('项目2专属报告')
    finish(savedReport('1'))
    await flushPromises()
    expect(wrapper.text()).toContain('项目2专属报告')
    expect(wrapper.text()).not.toContain('项目1专属报告')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true, projectId: '3' })
    await flushPromises()
    expect(wrapper.text()).not.toContain('项目2专属报告')
    wrapper.unmount()
  })

  it('ignores a late business map from the previous project', async () => {
    mockNoReport()
    let finish!: (map: ReturnType<typeof emptyMap>) => void
    vi.spyOn(projectApi, 'getBusinessMap')
      .mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockResolvedValue({ ...emptyMap('new'), onboarding: { ...emptyMap().onboarding, purpose: '新项目业务' } })
    const wrapper = mount(SourceStructureModal, { props: { open: true, projectId: '1' } })
    await wrapper.setProps({ projectId: '2' })
    await flushPromises()
    finish({ ...emptyMap('old'), onboarding: { ...emptyMap().onboarding, purpose: '旧项目业务' } })
    await flushPromises()
    expect(wrapper.text()).toContain('新项目业务')
    expect(wrapper.text()).not.toContain('旧项目业务')
    wrapper.unmount()
  })

})
