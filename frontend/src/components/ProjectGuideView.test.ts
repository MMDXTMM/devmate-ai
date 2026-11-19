import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectGuideView from './ProjectGuideView.vue'
import { guideFixture } from '../test/fixtures/projectGuide'

describe('ProjectGuideView', () => {
  it('exposes the glossary and treats unverified implementation separately', () => {
    const guide = guideFixture()
    guide.content.project.conclusionStatus = 'UNKNOWN'
    guide.content.glossary = [{ term: '签名', explanation: '检测内容是否被修改' }]
    const wrapper = mount(ProjectGuideView, { props: { guide } })
    expect(wrapper.get('.guide-hero').text()).toContain('本轮未核实实现')
    expect(wrapper.get('.guide-glossary').attributes('open')).toBeUndefined()
    expect(wrapper.get('.guide-glossary').text()).toContain('检测内容是否被修改')
  })
  it('restores a valid module in the same snapshot and rejects an outdated selection', async () => {
    window.history.replaceState(null, '', '#/projects/1?revision=abc&item=module:convert')
    const wrapper = mount(ProjectGuideView, { props: { guide: guideFixture(), projectId: '1' } })
    expect(wrapper.get('.guide-detail').text()).toContain('整理记录')
    await wrapper.get('.guide-detail > button').trigger('click')
    expect(window.location.hash).not.toContain('item=')
    await wrapper.get('.guide-module-card').trigger('click')
    expect(new URLSearchParams(window.location.hash.split('?')[1]).get('item')).toBe('module:convert')
    await wrapper.setProps({ guide: { ...guideFixture(), snapshot: { ...guideFixture().snapshot, revision: 'new' } } })
    expect(wrapper.find('.guide-detail').exists()).toBe(false)
    wrapper.unmount()
  })
  it('starts with purpose, capabilities and a short flow without source details', () => {
    const wrapper = mount(ProjectGuideView, {
      props: { guide: guideFixture() },
    })
    expect(wrapper.text()).toContain('帮助财务人员')
    expect(wrapper.get('.guide-module-card').text()).toContain('将明细整理成汇总')
    expect(wrapper.get('.guide-extra').attributes('open')).toBeUndefined()
    expect(wrapper.text()).toContain('关键判断：文件不存在就返回错误')
    expect(wrapper.text()).toContain('已观察到部分实现')
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
  it('expands implementation at a flow step and asks a scoped followup', async () => {
    const wrapper = mount(ProjectGuideView, { props: { guide: guideFixture() } })
    await wrapper.get('.guide-module-card').trigger('click')
    const step = wrapper.get('.guide-step-implementation')
    expect(wrapper.find('pre').exists()).toBe(false)
    ;(step.element as HTMLDetailsElement).open = true
    await step.trigger('toggle')
    expect(step.get('pre').text()).toContain('<script>example</script>')
    expect(step.find('script').exists()).toBe(false)
    const ask = wrapper.findAll('.guide-flow-detail button').find(button => button.text() === '追问这一步')!
    await ask.trigger('click')
    expect(wrapper.emitted('chat')?.[0]?.[0]).toContain('关键判断、数据变化及失败分支')
    wrapper.unmount()
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
