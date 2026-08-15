import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ProjectUnderstandingWorkspace from './ProjectUnderstandingWorkspace.vue'
import { projectApi } from '../services/projectApi'
import type { Project } from '../types/project'

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
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('从这里理解项目')
    expect(wrapper.text()).toContain('1 个迁移文件')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('已解析调用关系')
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('浏览代码结构')
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('向项目提问')
    expect(wrapper.get('.workspace-primary-actions').text()).toContain('开始代码审查')
    expect(wrapper.get('details.advanced-analysis').attributes('open')).toBeUndefined()
  })

  it('passes a suggested learning question to project search', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
      props: { project, parsing: false, rebuilding: false, deleting: false, reviewing: false },
    })
    await flushPromises()

    await wrapper.get('.question-examples button').trigger('click')

    expect(wrapper.emitted('search')?.[0]?.[0]).toContain('Controller Service')
  })

  it('starts the review workflow and explains a persisted failure stage', async () => {
    vi.spyOn(projectApi, 'listSourceDocuments').mockResolvedValue([])
    vi.spyOn(projectApi, 'listSourceReferences').mockResolvedValue([])
    const wrapper = mount(ProjectUnderstandingWorkspace, {
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
})
