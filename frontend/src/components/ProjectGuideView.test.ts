import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectGuideView from './ProjectGuideView.vue'
import { guideFixture } from '../test/fixtures/projectGuide'

describe('ProjectGuideView', () => {
  it('starts with purpose, capabilities and a short flow without source details', () => {
    const wrapper = mount(ProjectGuideView, {
      props: { guide: guideFixture() },
    })
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(wrapper.text()).toContain('读取表格')
    expect(wrapper.text()).toContain('关键判断：文件不存在就返回错误')
    expect(wrapper.text()).toContain('部分实现或演示')
    expect(wrapper.find('.guide-detail').exists()).toBe(false)
    expect(wrapper.find('pre').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('src/main.py')
    expect(wrapper.get('.guide-coverage').attributes('open')).toBeUndefined()
  })
  it('opens business details first and exposes real code only on explicit evidence action', async () => {
    const wrapper = mount(ProjectGuideView, {
      props: { guide: guideFixture() },
    })
    await wrapper.get('.guide-module-card').trigger('click')
    expect(wrapper.get('.guide-detail').text()).toContain('参与者：使用者')
    expect(wrapper.get('.guide-detail').text()).toContain(
      '文件不存在就返回错误',
    )
    expect(wrapper.find('pre').exists()).toBe(false)
    await wrapper.get('.guide-detail-actions button').trigger('click')
    expect(wrapper.get('.guide-evidence').text()).toContain('src/main.py:1–5')
    expect(wrapper.get('pre').text()).toContain('<script>example</script>')
    expect(wrapper.find('script').exists()).toBe(false)
    await wrapper.get('.guide-detail-actions .primary').trigger('click')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('整理记录')
    await wrapper.get('.guide-detail > button').trigger('click')
    expect(wrapper.find('.guide-detail').exists()).toBe(false)
  })
  it('decorates modules and opens business details from the interactive map', async () => {
    const guide = guideFixture()
    guide.content.appearance = { projectIcon: '🔭', modules: [{ moduleId: 'convert', icon: '🧮' }, { moduleId: 'user', icon: '👤' }] }
    guide.content.modules.push({ ...guide.content.modules[0]!, id: 'user', name: '用户身份', relatedModuleIds: [] })
    guide.content.modules[0]!.relatedModuleIds = ['user']
    const wrapper = mount(ProjectGuideView, { props: { guide } })
    expect(wrapper.get('.guide-mascot').text()).toBe('🔭')
    expect(wrapper.get('.diagram-module-picker button span').text()).toBe('🧮')
    expect(wrapper.findAll('.guide-module-emoji').map(item => item.text())).toEqual(['🧮', '👤'])
    await wrapper.get('.relation-neighbor').trigger('click')
    expect(wrapper.get('.guide-detail').text()).toContain('用户身份')
    await wrapper.get('.guide-detail > button').trigger('click')
    await wrapper.get('.diagram-tabs button:nth-child(2)').trigger('click')
    await wrapper.get('.journey-heading button').trigger('click')
    expect(wrapper.get('.guide-detail').text()).toContain('整理一次表格')
    expect(wrapper.find('pre').exists()).toBe(false)
  })
  it('handles document-only planned guides without fake workflows and resets selection after refresh', async () => {
    const guide = guideFixture()
    guide.content.flows = []
    guide.content.project.implementationStatus = 'PLANNED'
    const wrapper = mount(ProjectGuideView, { props: { guide } })
    expect(wrapper.text()).toContain('仅计划')
    expect(wrapper.find('.guide-flow-preview').exists()).toBe(false)
    await wrapper.get('.guide-module-card').trigger('click')
    expect(wrapper.text()).toContain('还没有足够证据')
    await wrapper.setProps({ guide: guideFixture() })
    expect(wrapper.find('.guide-detail').exists()).toBe(false)
  })
})
