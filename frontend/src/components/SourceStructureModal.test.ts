import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SourceStructureModal from './SourceStructureModal.vue'
import { projectApi } from '../services/projectApi'

describe('SourceStructureModal', () => {
  afterEach(() => vi.restoreAllMocks())

  it('opens with a business map and shows API flow plus implementation code', async () => {
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue({
      revision: 'abcdef',
      analysisMode: 'STATIC_CODE_EVIDENCE_V1',
      summary: '识别到项目管理模块。',
      moduleCount: 1,
      endpointCount: 1,
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

    const wrapper = mount(SourceStructureModal, {
      props: { open: true, projectId: '1', projectName: 'demo' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('业务地图')
    expect(wrapper.text()).toContain('项目管理')
    expect(wrapper.text()).toContain('POST /api/projects')
    expect(wrapper.text()).toContain('总体调用逻辑')
    expect(wrapper.text()).toContain('create() → save()')
    expect(wrapper.get('.business-evidence-list pre code').text()).toContain('@PostMapping')
    expect(projectApi.getBusinessFeatureDetail).toHaveBeenCalledWith('1', '11')
  })

  it('shows the selected symbol as a real code block with Chinese guidance', async () => {
    vi.spyOn(projectApi, 'getBusinessMap').mockResolvedValue({
      revision: 'abcdef', analysisMode: 'STATIC_CODE_EVIDENCE_V1', summary: '',
      moduleCount: 0, endpointCount: 0, modules: [], limitations: [],
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
})
