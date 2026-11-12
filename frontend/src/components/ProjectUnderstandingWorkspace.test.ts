import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ProjectUnderstandingWorkspace from './ProjectUnderstandingWorkspace.vue'
import { projectApi } from '../services/projectApi'
import { guideFixture } from '../test/fixtures/projectGuide'
import type { Project, ReviewWorkflow } from '../types/project'

const project: Project = {
  id: '2084116785588305922',
  name: 'devmate-ai',
  description: '智能项目理解助手',
  sourceType: 'GIT',
  sourceLocation: 'https://github.com/MMDXTMM/devmate-ai.git',
  defaultBranch: 'main',
  currentRevision: '0123456789abcdef',
  currentStructureVersion: 'source-structure-v2',
  status: 'READY',
  createdAt: '2026-08-03T00:00:00Z',
  updatedAt: '2026-08-07T00:00:00Z',
  lastIndexedAt: '2026-08-07T00:00:00Z',
}

function workflow(status: ReviewWorkflow['status']): ReviewWorkflow {
  return {
    id: '30', projectId: project.id, attemptKey: '123e4567-e89b-42d3-a456-426614174000',
    status, currentStage: status === 'SUCCEEDED' ? 'COMPLETED' : 'AGENT_REVIEW',
    indexTaskId: '31', reviewTaskId: '32', staticAnalysisTaskId: '33',
    ...(status === 'FAILED' ? { errorMessage: '模型服务暂时不可用', recoveryAction: '测试模型连接后重试' } : {}),
    createdAt: '2026-08-07T00:00:00Z',
  }
}

describe('ProjectUnderstandingWorkspace', () => {
  it('summarizes parsed evidence and prioritizes understanding actions', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([
      {
        id: '1', fileName: 'ProjectController.java', filePath: 'src/ProjectController.java',
        sourceKind: 'SOURCE_CODE', fileType: 'JAVA', packageName: 'com.example.project',
        revision: project.currentRevision!, structureVersion: 'source-structure-v2', status: 'PARSED', chunkCount: 5,
      },
      {
        id: '2', fileName: 'V1__schema.sql', filePath: 'db/V1__schema.sql',
        sourceKind: 'DATABASE_SCHEMA', fileType: 'SQL', revision: project.currentRevision!,
        structureVersion: 'source-structure-v2', status: 'PARSED', chunkCount: 3,
      },
    ])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([
      {
        id: '10', referenceKind: 'METHOD_CALL', referenceName: 'create', sourceChunkId: '11',
        sourceSymbolName: 'ProjectController#create', startLine: 10, endLine: 10, resolved: true,
      },
    ])

    const wrapper = mount(ProjectUnderstandingWorkspace, {
      global: { stubs: { WorkspaceProjectGuide: true } },
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('从这里理解项目')
    expect(wrapper.text()).toContain('1 个迁移文件')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('已解析调用关系')
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('理解项目业务')
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('向项目提问')
    expect(wrapper.get('.advanced-analysis').text()).toContain('开始代码审查')
    expect(wrapper.get('.workspace-primary-actions').text()).not.toContain('开始代码审查')
    expect(wrapper.get('details.advanced-analysis').attributes('open')).toBeUndefined()
  })

  it('shows the project guide directly inside the workspace without opening a modal', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    vi.spyOn(projectApi, 'latestUnderstandingReport').mockResolvedValue({
      id: '10', projectId: project.id, revision: project.currentRevision!, provider: 'COMPATIBLE', modelName: 'example',
      promptVersion: 'project-guide-v2', status: 'SUCCEEDED', guide: guideFixture(), businessFlows: [], readingGuide: [],
      risksAndUnknowns: [], promptTokens: 1, completionTokens: 1, totalTokens: 2, attemptKey: 'test', createdAt: project.createdAt,
    })
    const create = vi.spyOn(projectApi, 'createUnderstandingReport')
    const wrapper = mount(ProjectUnderstandingWorkspace, { props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false } })
    await flushPromises()
    expect(wrapper.find('.workspace-project-guide .project-guide').exists()).toBe(true)
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(wrapper.text()).toContain('先检查，再汇总')
    expect(wrapper.emitted('structure')).toBeUndefined()
    expect(create).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not offer Java review for a known text-only project', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([{
      id: '1', fileName: 'main.py', filePath: 'main.py', sourceKind: 'SOURCE_CODE', fileType: 'TEXT',
      revision: project.currentRevision!, structureVersion: 'source-structure-v2', status: 'PARSED', chunkCount: 1,
    }])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, { global: { stubs: { WorkspaceProjectGuide: true } },
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false } })
    await flushPromises()
    expect(wrapper.find('.review-primary-action').exists()).toBe(false)
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('理解项目业务')
  })

  it('passes a suggested business question to the AI conversation', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
      global: { stubs: { WorkspaceProjectGuide: true } },
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false },
    })
    await flushPromises()

    await wrapper.get('.question-examples button').trigger('click')

    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('业务')
  })

  it('starts the review workflow and explains a persisted failure stage', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
      global: { stubs: { WorkspaceProjectGuide: true } },
      props: {
        project,
        parsing: false,
        rebuilding: false,
        deleting: false,
        reviewing: false,
        reviewWorkflow: {
          id: '30', projectId: project.id, attemptKey: '123e4567-e89b-42d3-a456-426614174000',
          status: 'FAILED', currentStage: 'EMBEDDING', indexTaskId: '31', reviewTaskId: '32',
          staticAnalysisTaskId: '33', errorMessage: 'RAG索引构建失败',
          recoveryAction: '检查Embedding配置后重试', createdAt: '2026-08-07T00:00:00Z',
        },
      },
    })
    await flushPromises()

    await wrapper.get('.review-primary-action').trigger('click')

    expect(wrapper.emitted('runReview')).toHaveLength(1)
    expect(wrapper.get('.review-workflow-status').text()).toContain('RAG索引构建失败')
    expect(wrapper.get('.review-workflow-status').text()).toContain('检查Embedding配置后重试')
    expect(wrapper.findAll('.review-workflow-status li.completed')).toHaveLength(3)
    expect(wrapper.findAll('.review-workflow-status li.failed')).toHaveLength(1)
  })

  it('uses explicit actions for a running persisted workflow', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
      global: { stubs: { WorkspaceProjectGuide: true } },
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false, reviewWorkflow: workflow('RUNNING') },
    })
    await flushPromises()

    const action = wrapper.get('.review-primary-action')
    expect(action.attributes('disabled')).toBeDefined()
    expect(action.text()).toContain('审查进行中')
    expect(wrapper.get('.review-workflow-toolbar').text()).toContain('刷新恢复任务状态')
  })

  it('offers report and refresh actions after success', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
      global: { stubs: { WorkspaceProjectGuide: true } },
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false, reviewWorkflow: workflow('SUCCEEDED') },
    })
    await flushPromises()

    expect(wrapper.get('.review-primary-action').text()).toContain('重新审查')
    expect(wrapper.get('.review-workflow-toolbar').text()).toContain('查看最近审查报告')
    await wrapper.get('.review-workflow-toolbar button:last-child').trigger('click')
    expect(wrapper.emitted('refreshReview')).toHaveLength(1)
  })
})
