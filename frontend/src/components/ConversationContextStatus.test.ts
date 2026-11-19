import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConversationContextStatus from './ConversationContextStatus.vue'
describe('ConversationContextStatus', () => {
  it('shows actual tool outcomes, memory provenance and coverage warnings without exposing arguments', () => {
    const wrapper = mount(ConversationContextStatus, { props: { context: {
      actions: [{ tool: 'read_code', status: 'SUCCEEDED', evidenceCount: 3 }, { tool: 'read_code', status: 'REPEATED', evidenceCount: 3 }],
      warnings: ['代码证据达到总预算'], memoryUsed: true,
    } } })
    expect(wrapper.get('summary').text()).toContain('2 次工具请求')
    expect(wrapper.text()).toContain('读取代码：已完成')
    expect(wrapper.text()).toContain('已跳过重复请求')
    expect(wrapper.text()).toContain('较早的用户问题主题')
    expect(wrapper.get('[aria-label="上下文覆盖提示"]').text()).toContain('总预算')
    expect(wrapper.find('details').attributes('open')).toBeUndefined()
  })
  it('does not claim full project understanding when no limit is reached', () => {
    const wrapper = mount(ConversationContextStatus, { props: { context: { actions: [], warnings: [], memoryUsed: false } } })
    expect(wrapper.text()).toContain('静态代码阅读仍不等于运行验证')
    expect(wrapper.find('ol').exists()).toBe(false)
  })
})
