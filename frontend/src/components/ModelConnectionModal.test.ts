import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelConnectionModal from './ModelConnectionModal.vue'
import { ApiError } from '../services/apiClient'
import { modelConnectionApi } from '../services/modelConnectionApi'

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
    await wrapper.get('.model-provider-grid button').trigger('click')
    await flushPromises()
    expect(modelConnectionApi.update).toHaveBeenCalledWith('DEEPSEEK', 'deepseek-v4-flash', 'secret-key')
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
    await wrapper.get('select').setValue('deepseek-v4-pro')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(wrapper.get('input[type="password"]').element).toHaveProperty('value', '')
    expect(wrapper.get('select').element).toHaveProperty('value', 'deepseek-v4-flash')
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

  it('renders an administrator-defined compatible endpoint and submits its approved model', async () => {
    vi.spyOn(modelConnectionApi, 'list').mockResolvedValue([{
      provider: 'COMPATIBLE', displayName: 'OpenAI 兼容／中转站', baseUrl: 'https://compatible.example/v1',
      models: ['test-model'], selectedModel: 'test-model', configured: false, active: false,
    }])
    const update = vi.spyOn(modelConnectionApi, 'update').mockResolvedValue([])
    const wrapper = mount(ModelConnectionModal, { props: { open: true } })
    await flushPromises()
    expect(wrapper.text()).toContain('OpenAI 兼容／中转站')
    expect(wrapper.text()).toContain('https://compatible.example/v1')
    expect(wrapper.find('input[type="url"]').exists()).toBe(false)
    await wrapper.get('input[type="password"]').setValue('test-only-key')
    await wrapper.get('.model-provider-grid button').trigger('click')
    await flushPromises()
    expect(update).toHaveBeenCalledWith('COMPATIBLE', 'test-model', 'test-only-key')
    wrapper.unmount()
  })

})
