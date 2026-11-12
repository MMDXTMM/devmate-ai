import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SourceStructureModal from './SourceStructureModal.vue'
import { ApiError, projectApi } from '../services/projectApi'

describe('SourceStructureModal', () => {
  afterEach(() => vi.restoreAllMocks())

  it('opens directly with business interfaces and code without repeating the home guide', async () => {
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
    const latest = vi.spyOn(projectApi, 'latestUnderstandingReport')
    const create = vi.spyOn(projectApi, 'createUnderstandingReport')

    const wrapper = mount(SourceStructureModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('新人导览')
    expect(wrapper.find('.project-guide').exists()).toBe(false)
    expect(wrapper.find('.onboarding-view').exists()).toBe(false)
    expect(wrapper.findAll('.source-view-tabs button')).toHaveLength(2)
    expect(wrapper.get('.source-view-tabs button.active').text()).toContain('Java 实现与接口')
    expect(latest).not.toHaveBeenCalled()
    expect(create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('总体调用逻辑')
    expect(wrapper.text()).toContain('create() → save()')
    expect(wrapper.get('.business-evidence-list pre code').text()).toContain('@PostMapping')
    expect(projectApi.getBusinessFeatureDetail).toHaveBeenCalledWith('1', '11')
  })

  it('shows the selected symbol as a real code block with Chinese guidance', async () => {
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
    await wrapper.get('.source-view-tabs button:nth-child(2)').trigger('click')
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
  it('ignores a late business map from the previous project', async () => {
    let finish!: (map: ReturnType<typeof emptyMap>) => void
    vi.spyOn(projectApi, 'getBusinessMap')
      .mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockRejectedValueOnce(new ApiError('新项目业务加载失败', undefined, 500))
    const wrapper = mount(SourceStructureModal, { props: { open: true, projectId: '1' } })
    await wrapper.setProps({ projectId: '2' })
    await flushPromises()
    finish({ ...emptyMap('old'), summary: '旧项目业务' })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('新项目业务加载失败')
    expect(wrapper.text()).not.toContain('旧项目业务')
    wrapper.unmount()
  })

})
