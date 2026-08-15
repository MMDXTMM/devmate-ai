import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { projectApi } from './services/projectApi'
import { setAuthSession } from './services/authSession'
import type { Project } from './types/project'

describe('App project entry', () => {
  it('keeps advanced engineering operations out of the project list', async () => {
    const project: Project = {
      id: '1', name: 'demo', description: 'demo project', sourceType: 'GIT',
      sourceLocation: 'https://github.com/example/demo.git', defaultBranch: 'main',
      currentRevision: '0123456789abcdef', currentStructureVersion: 'source-structure-v2',
      status: 'READY', createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-07T00:00:00Z',
    }
    setAuthSession({
      accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
      user: { id: '1', username: 'demo' },
    })
    vi.spyOn(projectApi, 'list').mockResolvedValue({
      page: 1, size: 10, total: 1, pages: 1, items: [project],
    })

    const wrapper = mount(App, {
      global: {
        stubs: {
          ProjectUnderstandingWorkspace: { template: '<div data-testid="understanding-workspace">理解工作台</div>' },
        },
      },
    })
    await flushPromises()

    const actions = wrapper.get('.row-actions').text()
    expect(actions).toContain('打开理解工作台')
    expect(actions).not.toContain('Diff')
    expect(actions).not.toContain('静态分析')
    expect(actions).not.toContain('向量化')
    expect(actions).not.toContain('AI审查')
    expect(actions).not.toContain('评测')

    await wrapper.get('.open-workspace').trigger('click')
    expect(wrapper.get('[data-testid="understanding-workspace"]').exists()).toBe(true)
  })

  it('opens a READY understanding workspace after parsing even when the active filter hides the project', async () => {
    const project: Project = {
      id: '2', name: 'new-demo', sourceType: 'GIT', sourceLocation: 'https://github.com/example/demo.git',
      defaultBranch: 'main', status: 'CREATED', createdAt: '2026-08-07T00:00:00Z', updatedAt: '2026-08-07T00:00:00Z',
    }
    setAuthSession({
      accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
      user: { id: '1', username: 'demo' },
    })
    vi.spyOn(projectApi, 'list')
      .mockResolvedValueOnce({ page: 1, size: 10, total: 1, pages: 1, items: [project] })
      .mockResolvedValueOnce({ page: 1, size: 10, total: 0, pages: 0, items: [] })
    vi.spyOn(projectApi, 'importSource').mockResolvedValue({
      id: '20', projectId: project.id, taskType: 'FULL', revision: '0123456789abcdef',
      structureVersion: 'source-structure-v2', status: 'SUCCEEDED', totalFiles: 1, processedFiles: 1,
      reusedFiles: 0, failedFiles: 0, cloneDurationMs: 1, scanDurationMs: 1, planDurationMs: 1,
      parseDurationMs: 1, persistDurationMs: 1, totalDurationMs: 5, createdAt: '2026-08-07T00:00:00Z',
      finishedAt: '2026-08-07T00:00:01Z',
    })
    vi.spyOn(projectApi, 'indexEmbeddings').mockResolvedValue({
      id: '21', projectId: project.id, revision: '0123456789abcdef', provider: 'LOCAL', modelName: 'code-hash-v1',
      dimensions: 256, status: 'SUCCEEDED', totalChunks: 3, processedChunks: 3, skippedChunks: 0,
      reusedChunks: 0, failedChunks: 0, createdAt: '2026-08-07T00:00:00Z',
    })

    const wrapper = mount(App, {
      global: {
        stubs: {
          ProjectUnderstandingWorkspace: {
            props: ['project'],
            template: '<div data-testid="understanding-status">{{ project.status }} {{ project.currentStructureVersion }}</div>',
          },
        },
      },
    })
    await flushPromises()
    await wrapper.get('.parse-project').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="understanding-status"]').text()).toBe('READY source-structure-v2')
    expect(projectApi.importSource).toHaveBeenCalledWith(project.id)
    expect(projectApi.indexEmbeddings).toHaveBeenCalledWith(project.id)
  })

  it('runs the complete review workflow from the primary workspace action', async () => {
    const project: Project = {
      id: '3', name: 'review-demo', sourceType: 'GIT', sourceLocation: 'https://github.com/example/review.git',
      defaultBranch: 'main', currentRevision: 'a'.repeat(40), status: 'READY',
      createdAt: '2026-08-07T00:00:00Z', updatedAt: '2026-08-07T00:00:00Z',
    }
    setAuthSession({
      accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
      user: { id: '1', username: 'demo' },
    })
    vi.spyOn(projectApi, 'list').mockResolvedValue({
      page: 1, size: 10, total: 1, pages: 1, items: [project],
    })
    vi.spyOn(projectApi, 'createReviewWorkflow').mockResolvedValue({
      id: '30', projectId: project.id, attemptKey: '123e4567-e89b-42d3-a456-426614174000',
      status: 'SUCCEEDED', currentStage: 'COMPLETED', aiReviewTaskId: '31',
      createdAt: '2026-08-07T00:00:00Z',
    })

    const wrapper = mount(App, {
      global: {
        stubs: {
          ProjectUnderstandingWorkspace: {
            emits: ['runReview'],
            template: '<button data-testid="run-review" @click="$emit(\'runReview\')">开始代码审查</button>',
          },
          AiReviewModal: { template: '<div data-testid="ai-review-report">审查报告</div>' },
        },
      },
    })
    await flushPromises()
    await wrapper.get('.open-workspace').trigger('click')
    await wrapper.get('[data-testid="run-review"]').trigger('click')
    await flushPromises()

    expect(projectApi.createReviewWorkflow).toHaveBeenCalledWith(project.id)
    expect(wrapper.get('[data-testid="ai-review-report"]').exists()).toBe(true)
  })
})
