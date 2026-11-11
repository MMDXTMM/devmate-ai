import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelAnswer from './ModelAnswer.vue'

describe('readable code-grounded answers', () => {
  it('renders headings, emphasis, lists and unclosed streaming code without HTML execution', () => {
    const wrapper = mount(ModelAnswer, { props: { content: '## 业务流程\n\n**创建订单** 后调用 `OrderService`。\n\n1. 校验参数\n2. 保存订单\n\n<img src=x onerror=alert(1)>\n\n```java\nvoid create() {}' } })
    expect(wrapper.get('h3').text()).toBe('业务流程')
    expect(wrapper.get('strong').text()).toBe('创建订单')
    expect(wrapper.get('.inline-code').text()).toBe('OrderService')
    expect(wrapper.findAll('ol li').map(item => item.text())).toEqual(['校验参数', '保存订单'])
    expect(wrapper.get('pre code').text()).toBe('void create() {}')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img')
    wrapper.unmount()
  })
})
