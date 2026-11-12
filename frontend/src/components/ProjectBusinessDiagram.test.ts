import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectBusinessDiagram from './ProjectBusinessDiagram.vue'
import { guideFixture } from '../test/fixtures/projectGuide'

function connectedGuide() {
  const guide = guideFixture()
  guide.content.modules.push({ ...guide.content.modules[0]!, id: 'export', name: '导出表格', relatedModuleIds: [] })
  guide.content.modules[0]!.relatedModuleIds = ['export']
  guide.content.flows[0]!.steps.push({ ...guide.content.flows[0]!.steps[0]!, action: '导出结果', result: '获得汇总文件', moduleIds: ['export'] })
  return guide
}

describe('ProjectBusinessDiagram', () => {
  it('shows only saved module relations and lets readers open either end', async () => {
    const wrapper = mount(ProjectBusinessDiagram, { props: { guide: connectedGuide() } })
    expect(wrapper.findAll('.relation-wires path')).toHaveLength(1)
    expect(wrapper.get('.relation-focus').text()).toContain('整理记录')
    expect(wrapper.get('.relation-neighbor').text()).toContain('导出表格')
    expect(wrapper.text()).toContain('不表示调用方向')
    await wrapper.get('.relation-neighbor').trigger('click')
    expect(wrapper.emitted('selectModule')?.[0]).toEqual(['export'])
    await wrapper.get('.diagram-module-picker button:nth-child(2)').trigger('click')
    expect(wrapper.get('.relation-focus').text()).toContain('导出表格')
    expect(wrapper.get('.relation-neighbor').text()).toContain('整理记录')
  })

  it('renders ordered steps and participating modules without inferring relationships', async () => {
    const guide = connectedGuide()
    guide.content.modules[0]!.relatedModuleIds = []
    const wrapper = mount(ProjectBusinessDiagram, { props: { guide } })
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.text()).toContain('尚未确认该模块')
    await wrapper.get('.diagram-tabs button:nth-child(2)').trigger('click')
    const steps = wrapper.findAll('.journey-track > li')
    expect(steps.map(step => step.get('b').text())).toEqual(['从这里开始', '检查输入', '导出结果', '最后得到'])
    expect(steps[1]!.text()).toContain('整理记录')
    expect(steps[2]!.text()).toContain('导出表格')
    await wrapper.get('.journey-heading button').trigger('click')
    expect(wrapper.emitted('selectFlow')?.[0]).toEqual(['run'])
  })

  it('handles empty, unknown targets and many modules and resets when project changes', async () => {
    const guide = guideFixture()
    guide.content.modules[0]!.relatedModuleIds = ['missing']
    guide.content.flows = []
    const wrapper = mount(ProjectBusinessDiagram, { props: { guide } })
    expect(wrapper.find('svg').exists()).toBe(false)
    await wrapper.get('.diagram-tabs button:nth-child(2)').trigger('click')
    expect(wrapper.text()).toContain('还不足以串起业务路线')
    const next = connectedGuide()
    for (let i = 0; i < 18; i++) next.content.modules.push({ ...next.content.modules[0]!, id: `extra-${i}`, name: `其他模块${i}`, relatedModuleIds: ['convert'] })
    await wrapper.setProps({ guide: next })
    expect(wrapper.findAll('.diagram-module-picker button')).toHaveLength(20)
    expect(wrapper.findAll('.relation-wires path')).toHaveLength(19)
    expect(wrapper.get('.diagram-scroll').attributes('tabindex')).toBe('0')
    expect(wrapper.text()).not.toContain('missing')
    await wrapper.setProps({ guide: { ...next, content: { ...next.content, modules: [], flows: [] } } })
    expect(wrapper.text()).toContain('当前没有可展示的模块')
  })
})
