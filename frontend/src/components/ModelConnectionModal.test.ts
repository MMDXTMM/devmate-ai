import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelConnectionModal from './ModelConnectionModal.vue'
import { ApiError } from '../services/apiClient'
import { modelConnectionApi } from '../services/modelConnectionApi'
import type { ModelProvider } from '../types/modelConnection'

enableAutoUnmount(afterEach)

const providers = [{
  provider: 'DEEPSEEK' as const,
  displayName: 'DeepSeek',
  baseUrl: 'https://api.deepseek.com',
  models: ['deepseek-v4-flash', 'deepseek-v4-pro'],
  configured: false,
  active: false,
  selectedModel: 'deepseek-v4-flash',
}]

describe('ModelConnectionModal', () => {
  afterEach(() => vi.restoreAllMocks())

  it('configures, masks and tests a selectable model connection', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(providers)
    vi.spyOn(modelConnectionApi, 'update').mockResolvedValue([{ ...providers[0], configured: true, active: true }])
    vi.spyOn(modelConnectionApi, 'test').mockResolvedValue({
      provider: 'DEEPSEEK', model: 'deepseek-v4-flash', latencyMs: 88, message: '连接成功',
    })
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()

    expect(wrapper.text()).toContain('大模型连接中心')
    expect(wrapper.text()).toContain('DeepSeek')
    expect(wrapper.get('input[type="password"]').attributes('type')).toBe('password')
    await wrapper.get('input[type="password"]').setValue('secret-key')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(modelConnectionApi.update).toHaveBeenCalledWith('DEEPSEEK', 'deepseek-v4-flash', 'secret-key')
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
    expect(wrapper.get('.saved-connection-choice').attributes('aria-pressed')).toBe('true')
    await wrapper.get('button[aria-label="编辑 DeepSeek 连接"]').trigger('click')
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')

    await wrapper.get('.model-modal-footer button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('DEEPSEEK / deepseek-v4-flash · 88ms')
  })
  it('discards an unsaved key on close and reloads the saved model on reopen', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(providers)
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('input[type="password"]').setValue('unsaved-test-key')
    await wrapper.get('form label:nth-of-type(2) select').setValue('deepseek-v4-pro')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
    expect(wrapper.get('form label:nth-of-type(2) select').element).toHaveProperty('value', 'deepseek-v4-flash')
    wrapper.unmount()
  })

  it('shows a useful connection failure without leaving an old success result', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue([{ ...providers[0], configured: true, active: true }])
    vi.spyOn(modelConnectionApi, 'test')
      .mockResolvedValueOnce({ provider: 'DEEPSEEK', model: 'test', latencyMs: 10, message: '连接成功' })
      .mockRejectedValueOnce(new ApiError('API Key无效或没有模型权限'))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('.model-modal-footer button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('返回项目')
    await wrapper.get('.model-modal-footer button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('API Key无效')
    wrapper.unmount()
  })

  it('edits a trusted compatible API root, model ID and explicit protocol', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue([{
      provider: 'COMPATIBLE', displayName: 'OpenAI 兼容／中转站', baseUrl: 'https://compatible.example/v1',
      models: ['test-model'], selectedModel: 'test-model', configured: false, active: false,
    }])
    const update = vi.spyOn(modelConnectionApi, 'update').mockResolvedValue([])
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    expect(wrapper.text()).toContain('OpenAI 兼容／中转站')
    expect(wrapper.text()).toContain('https://compatible.example/v1')
    expect(wrapper.get('input[type="url"]').element).toHaveProperty('value', 'https://compatible.example/v1')
    expect(wrapper.text()).toContain('Responses 是另一种协议')
    await wrapper.get('input[type="url"]').setValue('https://compatible.example/v1/chat/completions')
    await wrapper.get('input[type="text"]').setValue('vendor/custom-model')
    expect(wrapper.get('.relay-request-preview').text()).toContain('POST https://compatible.example/v1/chat/completions')
    await wrapper.get('input[type="password"]').setValue('test-only-key')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(update).toHaveBeenCalledWith('COMPATIBLE', 'vendor/custom-model', 'test-only-key', { baseUrl: 'https://compatible.example/v1/chat/completions', protocol: 'CHAT_COMPLETIONS' })
    wrapper.unmount()
  })

  it('lists only saved connections and switches using saved settings rather than an unsaved edit', async () => {
    const saved: ModelProvider[] = [
      { ...providers[0], configured: true, active: true },
      { provider: 'COMPATIBLE', displayName: '中转站', baseUrl: 'https://relay.example/v1',
        models: ['saved-model'], selectedModel: 'saved-model', configured: true, active: false },
      { ...providers[0], provider: 'OPENAI', displayName: 'OpenAI', configured: false, active: false },
    ]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(saved)
    const update = vi.spyOn(modelConnectionApi, 'update').mockResolvedValue(
      saved.map(item => ({ ...item, active: item.provider === 'COMPATIBLE' })),
    )
    const test = vi.spyOn(modelConnectionApi, 'test')
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    expect(wrapper.findAll('.saved-connection')).toHaveLength(2)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('.saved-connection-list').text()).not.toContain('OpenAI')

    await wrapper.get('button[aria-label="编辑 中转站 连接"]').trigger('click')
    await wrapper.get('input[type="url"]').setValue('https://unsaved.example/v1')
    await wrapper.get('input[type="text"]').setValue('unsaved-model')
    await wrapper.get('input[type="password"]').setValue('unsaved-key')
    await wrapper.get('button[aria-label="启用 中转站 · saved-model"]').trigger('click')
    await flushPromises()
    expect(update).toHaveBeenCalledExactlyOnceWith('COMPATIBLE', 'saved-model', '')
    expect(wrapper.get('button[aria-label="启用 中转站 · saved-model"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.model-modal-footer').text()).toContain('中转站 · saved-model')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(test).not.toHaveBeenCalled()

    await wrapper.get('button[aria-label="编辑 中转站 连接"]').trigger('click')
    expect(wrapper.get('input[type="url"]').element).toHaveProperty('value', 'https://relay.example/v1')
    expect(wrapper.get('input[type="text"]').element).toHaveProperty('value', 'saved-model')
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
  })

  it('retains the current connection on switching failure and blocks duplicate requests', async () => {
    const saved: ModelProvider[] = [
      { ...providers[0], configured: true, active: true },
      { ...providers[0], provider: 'OPENAI', displayName: 'OpenAI', selectedModel: 'gpt-5-mini', configured: true, active: false },
    ]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(saved)
    let rejectUpdate!: (error: Error) => void
    const update = vi.spyOn(modelConnectionApi, 'update').mockImplementation(() => new Promise((_, reject) => { rejectUpdate = reject }))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    const target = wrapper.get('button[aria-label="启用 OpenAI · gpt-5-mini"]')
    await target.trigger('click')
    await target.trigger('click')
    expect(update).toHaveBeenCalledTimes(1)
    expect(target.attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[aria-label="启用 DeepSeek · deepseek-v4-flash"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('切换中…')
    rejectUpdate(new ApiError('保存失败，请重试'))
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('保存失败')
    expect(target.attributes('aria-pressed')).toBe('false')
    expect(target.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.model-modal-footer').text()).toContain('DeepSeek · deepseek-v4-flash')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)

    update.mockResolvedValue(saved.map(item => ({ ...item, active: item.provider === 'OPENAI' })))
    await target.trigger('click')
    await flushPromises()
    expect(target.attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('adds one provider at a time and clears the key when cancelling or changing providers', async () => {
    const available: ModelProvider[] = [
      { ...providers[0], configured: true, active: true },
      { ...providers[0], provider: 'OPENAI', displayName: 'OpenAI', selectedModel: 'gpt-5-mini', configured: false, active: false },
    ]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(available)
    const update = vi.spyOn(modelConnectionApi, 'update')
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('.add-connection').trigger('click')
    expect(wrapper.findAll('input[type="password"]')).toHaveLength(1)
    expect(wrapper.get('form select').element).toHaveProperty('value', 'OPENAI')
    await wrapper.get('input[type="password"]').setValue('unsaved-key')
    await wrapper.get('form select').setValue('DEEPSEEK')
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
    expect(wrapper.get('input[type="password"]').attributes('placeholder')).toContain('留空则保留')
    await wrapper.get('input[type="password"]').setValue('another-unsaved-key')
    await wrapper.get('.connection-editor-actions button').trigger('click')
    await wrapper.get('button[aria-label="编辑 DeepSeek 连接"]').trigger('click')
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
    expect(update).not.toHaveBeenCalled()
  })

  it('ignores a completed switch from a previously closed modal', async () => {
    const saved: ModelProvider[] = [
      { ...providers[0], configured: true, active: false },
      { ...providers[0], provider: 'OPENAI', displayName: 'OpenAI', selectedModel: 'gpt-5-mini', configured: true, active: true },
    ]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(saved)
    let resolveUpdate!: (value: ModelProvider[]) => void
    vi.spyOn(modelConnectionApi, 'update').mockImplementation(() => new Promise(resolve => { resolveUpdate = resolve }))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('button[aria-label="启用 DeepSeek · deepseek-v4-flash"]').trigger('click')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await flushPromises()
    resolveUpdate(saved.map(item => ({ ...item, active: item.provider === 'DEEPSEEK' })))
    await flushPromises()
    expect(wrapper.get('.model-modal-footer').text()).toContain('OpenAI · gpt-5-mini')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('retries a failed configuration read without activating or testing a model', async () => {
    const list = vi.spyOn(modelConnectionApi, 'list').mockRejectedValueOnce(new ApiError('配置暂时不可用')).mockResolvedValueOnce(providers)
    const update = vi.spyOn(modelConnectionApi, 'update')
    const test = vi.spyOn(modelConnectionApi, 'test')
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('配置暂时不可用')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(list).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('还没有保存连接')
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.get('.model-modal-footer button').attributes('disabled')).toBeDefined()
    expect(update).not.toHaveBeenCalled()
    expect(test).not.toHaveBeenCalled()
  })

  it('confirms deleting an inactive connection and keeps the active connection usable', async () => {
    const saved: ModelProvider[] = [
      { ...providers[0], configured: true, active: true },
      { ...providers[0], provider: 'OPENAI', displayName: 'OpenAI', selectedModel: 'gpt-5-mini', configured: true, active: false },
    ]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(saved)
    const remove = vi.spyOn(modelConnectionApi, 'remove').mockResolvedValue(saved.map(item => item.provider === 'OPENAI' ? { ...item, configured: false } : item))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('button[aria-label="删除 OpenAI 连接"]').trigger('click')
    expect(wrapper.get('.connection-delete-confirm').text()).toContain('gpt-5-mini')
    expect(remove).not.toHaveBeenCalled()
    await wrapper.get('.connection-delete-confirm .secondary').trigger('click')
    expect(wrapper.find('.connection-delete-confirm').exists()).toBe(false)
    expect(wrapper.findAll('.saved-connection')).toHaveLength(2)
    await wrapper.get('button[aria-label="删除 OpenAI 连接"]').trigger('click')
    await wrapper.get('.connection-delete-confirm .danger').trigger('click')
    await flushPromises()
    expect(remove).toHaveBeenCalledExactlyOnceWith('OPENAI')
    expect(wrapper.findAll('.saved-connection')).toHaveLength(1)
    expect(wrapper.get('.model-modal-footer').text()).toContain('DeepSeek · deepseek-v4-flash')
    expect(wrapper.get('.model-modal-footer button').attributes('disabled')).toBeUndefined()
  })

  it('deletes the last active connection and requires a new key instead of reusing a draft', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue([{ ...providers[0], configured: true, active: true }])
    vi.spyOn(modelConnectionApi, 'remove').mockResolvedValue(providers)
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('button[aria-label="编辑 DeepSeek 连接"]').trigger('click')
    await wrapper.get('input[type="password"]').setValue('test-only-unsaved-key')
    await wrapper.get('button[aria-label="删除 DeepSeek 连接"]').trigger('click')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('.connection-delete-confirm').text()).toContain('当前启用')
    await wrapper.get('.connection-delete-confirm .danger').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有保存连接')
    expect(wrapper.text()).toContain('请重新选择或添加')
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
    expect(wrapper.get('input[type="password"]').attributes('required')).toBeDefined()
    expect(wrapper.get('.model-modal-footer button').attributes('disabled')).toBeDefined()
  })

  it('keeps saved settings on delete failure and blocks duplicate or simultaneous operations', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue([{ ...providers[0], configured: true, active: true }])
    let rejectDelete!: (error: Error) => void
    const remove = vi.spyOn(modelConnectionApi, 'remove').mockImplementation(() => new Promise((_, reject) => { rejectDelete = reject }))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('button[aria-label="删除 DeepSeek 连接"]').trigger('click')
    const confirm = wrapper.get('.connection-delete-confirm .danger')
    await confirm.trigger('click'); await confirm.trigger('click')
    expect(remove).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.saved-connection-choice').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.model-modal-footer button').attributes('disabled')).toBeDefined()
    rejectDelete(new ApiError('删除失败，请重试'))
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('删除失败')
    expect(wrapper.get('.saved-connection-choice').attributes('aria-pressed')).toBe('true')
    expect(confirm.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('ignores a delete result from a previously closed modal', async () => {
    const saved = [{ ...providers[0], configured: true, active: true }]
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue(saved)
    let resolveDelete!: (value: ModelProvider[]) => void
    vi.spyOn(modelConnectionApi, 'remove').mockImplementation(() => new Promise(resolve => { resolveDelete = resolve }))
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    await wrapper.get('button[aria-label="删除 DeepSeek 连接"]').trigger('click')
    await wrapper.get('.connection-delete-confirm .danger').trigger('click')
    await wrapper.setProps({ open: false }); await wrapper.setProps({ open: true })
    await flushPromises()
    resolveDelete(providers); await flushPromises()
    expect(wrapper.findAll('.saved-connection')).toHaveLength(1)
    expect(wrapper.get('.model-modal-footer').text()).toContain('DeepSeek')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })
})
