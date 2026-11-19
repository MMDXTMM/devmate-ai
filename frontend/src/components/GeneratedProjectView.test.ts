import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GeneratedProjectView from './GeneratedProjectView.vue'
import { generationApi } from '../services/generationApi'
import type { GenerationRun } from '../types/generation'

const succeeded: GenerationRun = { id: '2120000000000000001', sessionId: '2120000000000000002', versionId: '2120000000000000003', status: 'SUCCEEDED',
  summary: 'Python待办CLI', runInstructions: 'python main.py', files: [{ path: 'README.md', characters: 40 }, { path: 'main.py', characters: 80 }],
  warnings: ['未执行目标测试'], verification: 'STRUCTURE_ONLY', createdAt: '2026-11-13', updatedAt: '2026-11-13' }
const props = { sessionId: succeeded.sessionId, versionId: succeeded.versionId }
enableAutoUnmount(afterEach)
describe('GeneratedProjectView', () => {
  beforeEach(() => {
    vi.spyOn(generationApi, 'latestRun').mockResolvedValue(null)
    vi.spyOn(generationApi, 'file').mockResolvedValue({ path: 'README.md', content: '# Readme <script>alert(1)</script>' })
  })
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })
  it('reads without generating until the user explicitly starts a task', async () => {
    vi.mocked(generationApi.latestRun).mockResolvedValueOnce(null).mockResolvedValueOnce(succeeded)
    const generate = vi.spyOn(generationApi, 'generate').mockResolvedValue(succeeded)
    const wrapper = mount(GeneratedProjectView, { props }); await flushPromises()
    expect(generate).not.toHaveBeenCalled()
    await wrapper.get('.generation-delivery-state .primary').trigger('click'); await flushPromises()
    expect(generate).toHaveBeenCalledWith(props.sessionId, props.versionId, expect.any(String))
    expect(wrapper.text()).toContain('尚未安装依赖或运行代码')
    expect(wrapper.text()).toContain('此处预览和 ZIP 为初次生成的原始版本')
    expect(wrapper.get('.generated-source code').text()).toContain('<script>')
    expect(wrapper.find('script').exists()).toBe(false)
  })
  it('keeps the same attempt key after an uncertain network result', async () => {
    const generate = vi.spyOn(generationApi, 'generate').mockRejectedValue(new Error('network'))
    const wrapper = mount(GeneratedProjectView, { props }); await flushPromises()
    await wrapper.get('.generation-delivery-state .primary').trigger('click'); await flushPromises()
    await wrapper.get('.generation-delivery-state .primary').trigger('click'); await flushPromises()
    expect(generate).toHaveBeenCalledTimes(2)
    expect(generate.mock.calls[0][2]).toBe(generate.mock.calls[1][2])
  })
  it('offers an explicit retry after a failed task', async () => {
    vi.mocked(generationApi.latestRun).mockResolvedValue({ ...succeeded, status: 'FAILED', errorMessage: '模型超时' })
    const generate = vi.spyOn(generationApi, 'generate').mockResolvedValue(succeeded)
    const wrapper = mount(GeneratedProjectView, { props }); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('模型超时')
    expect(generate).not.toHaveBeenCalled()
    await wrapper.get('.generation-delivery-state .primary').trigger('click'); await flushPromises()
    expect(generate).toHaveBeenCalledTimes(1)
  })
  it('polls only reads for a running job and stops when unmounted', async () => {
    vi.useFakeTimers(); vi.mocked(generationApi.latestRun).mockResolvedValue({ ...succeeded, status: 'RUNNING' })
    const generate = vi.spyOn(generationApi, 'generate')
    const wrapper = mount(GeneratedProjectView, { props }); await flushPromises()
    await vi.advanceTimersByTimeAsync(2000); await flushPromises()
    expect(generationApi.latestRun).toHaveBeenCalledTimes(2); expect(generate).not.toHaveBeenCalled()
    wrapper.unmount(); await vi.advanceTimersByTimeAsync(10000)
    expect(generationApi.latestRun).toHaveBeenCalledTimes(2)
  })
  it('ignores a stale file preview after another file is selected', async () => {
    vi.mocked(generationApi.latestRun).mockResolvedValue(succeeded)
    let first!: (value: { path: string; content: string }) => void
    vi.mocked(generationApi.file).mockImplementationOnce(() => new Promise(resolve => { first = resolve })).mockResolvedValueOnce({ path: 'main.py', content: 'print("todo")' })
    const wrapper = mount(GeneratedProjectView, { props }); await flushPromises()
    await wrapper.findAll('.generated-files nav button')[1].trigger('click'); await flushPromises()
    first({ path: 'README.md', content: 'old preview' }); await flushPromises()
    expect(wrapper.get('.generated-source code').text()).toBe('print("todo")')
    expect(wrapper.get('.generated-source header').text()).toBe('main.py')
  })
})
