import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelConnectionModal from './ModelConnectionModal.vue'
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
})
