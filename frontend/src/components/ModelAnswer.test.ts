import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelAnswer from './ModelAnswer.vue'

describe('readable code-grounded answers', () => {
  afterEach(() => vi.unstubAllGlobals())
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
  it('renders tables, nested lists and block quotes as readable semantic elements', () => {
    const wrapper = mount(ModelAnswer, { props: { content: '| 接口 | 用途 |\n| --- | --- |\n| `GET /users` | 查询用户 |\n\n> 核对权限\n\n1. 入口\n   - 参数\n   - 权限\n2. 返回' } })
    expect(wrapper.findAll('th')).toHaveLength(2)
    expect(wrapper.get('td code').text()).toBe('GET /users')
    expect(wrapper.get('blockquote').text()).toContain('核对权限')
    expect(wrapper.findAll('ol > li')).toHaveLength(2)
    expect(wrapper.findAll('ul > li')).toHaveLength(2)
    wrapper.unmount()
  })

  it('blocks model HTML, unsafe links and automatic image requests', () => {
    const content = '<script>alert(1)</script>\n\n[攻击](javascript:alert(1)) ![跟踪](https://example.com/track)\n\n[文档](https://example.com/doc)'
    const wrapper = mount(ModelAnswer, { props: { content } })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.get('a').attributes()).toMatchObject({ href: 'https://example.com/doc', rel: 'noopener noreferrer' })
    expect(wrapper.text()).toContain('<script>')
    wrapper.unmount()
  })

  it('copies code without markup and reports clipboard denial', async () => {
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('denied'))
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    const wrapper = mount(ModelAnswer, { props: { content: '```java\nvoid run() {}\n```' } })
    await wrapper.get('.answer-code button').trigger('click')
    expect(writeText).toHaveBeenCalledWith('void run() {}\n')
    expect(wrapper.text()).toContain('已复制')
    await wrapper.get('.answer-code button').trigger('click')
    expect(wrapper.text()).toContain('复制失败')
    wrapper.unmount()
  })

})
