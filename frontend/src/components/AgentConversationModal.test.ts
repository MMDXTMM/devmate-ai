import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AgentConversationModal from './AgentConversationModal.vue'
import { ApiError } from '../services/apiClient'
import { conversationApi } from '../services/conversationApi'

describe('AgentConversationModal', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })
  it('creates a conversation, consumes SSE tokens, and reloads persisted messages', async () => {
    const conversation = {
      id: '100', projectId: '10', title: '新对话', status: 'ACTIVE' as const,
      revision: 'abc', provider: 'DEEPSEEK', modelName: 'deepseek-v4-flash',
      promptVersion: 'project-conversation-v1',
      createdAt: '2026-08-17T00:00:00Z', updatedAt: '2026-08-17T00:00:00Z',
    }
    vi.spyOn(conversationApi, 'list')
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ ...conversation, title: '订单如何创建？' }])
    vi.spyOn(conversationApi, 'create').mockResolvedValue(conversation)
    vi.spyOn(conversationApi, 'messages').mockResolvedValue([
      {
        id: '101', conversationId: '100', sequenceNo: 1, role: 'USER',
        content: '订单如何创建？', status: 'COMPLETED', evidence: [], createdAt: '2026-08-17T00:00:01Z',
      },
      {
        id: '102', conversationId: '100', sequenceNo: 2, role: 'ASSISTANT',
        content: '由 OrderService 创建。', status: 'COMPLETED', modelName: 'deepseek-v4-flash',
        evidence: [{ chunkId: '201', filePath: 'OrderService.java', symbolName: 'createOrder',
          startLine: 20, endLine: 22, excerpt: 'void createOrder() {}' }], createdAt: '2026-08-17T00:00:02Z',
      },
    ])
    vi.spyOn(conversationApi, 'streamMessage').mockImplementation(async (
      _projectId, _conversationId, _question, _attemptKey, handlers,
    ) => {
      handlers.onEvidence?.([{ chunkId: '201', filePath: 'OrderService.java', startLine: 20,
        endLine: 22, excerpt: 'void createOrder() {}' }])
      handlers.onToken?.({ content: '由 Order' })
      handlers.onToken?.({ content: 'Service 创建。' })
      handlers.onDone?.({} as never)
    })

    const wrapper = mount(AgentConversationModal, {
      props: { open: true, projectId: '10', projectName: '订单系统' },
    })
    await flushPromises()
    await wrapper.get('.conversation-list .button').trigger('click')
    await flushPromises()
    await wrapper.get('.agent-chat-input textarea').setValue('订单如何创建？')
    await wrapper.get('.agent-chat-input').trigger('submit')
    await flushPromises()

    expect(conversationApi.streamMessage).toHaveBeenCalledWith(
      '10', '100', '订单如何创建？', expect.any(String), expect.any(Object), expect.any(AbortSignal),
    )
    await vi.waitFor(() => expect(wrapper.text()).toContain('由 OrderService 创建。'))
    expect(wrapper.text()).toContain('OrderService.java')
    expect(wrapper.text()).toContain('代码证据')
    wrapper.unmount()
  })
  const conversation = { id: '100', projectId: '10', title: '测试对话', status: 'ACTIVE' as const,
    revision: 'abc', provider: 'DEEPSEEK', modelName: 'test', promptVersion: 'v1', createdAt: '', updatedAt: '' }

  it('prefills a suggested question without sending a paid request', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const stream = vi.spyOn(conversationApi, 'streamMessage')
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10', initialQuestion: '主要业务如何运行？' } })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('主要业务如何运行？')
    expect(stream).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('preserves a failed question and reuses the request key after an uncertain disconnection', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([conversation])
    vi.spyOn(conversationApi, 'messages').mockResolvedValue([])
    const stream = vi.spyOn(conversationApi, 'streamMessage').mockRejectedValue(new ApiError('连接中断'))
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    await wrapper.get('textarea').setValue('创建流程是什么？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const originalKey = stream.mock.calls[0][3]
    expect(wrapper.get('textarea').element.value).toBe('创建流程是什么？')
    expect(wrapper.get('[role="alert"]').text()).toContain('刷新历史')
    await wrapper.get('.refresh-chat').trigger('click')
    await flushPromises()
    expect(stream).toHaveBeenCalledTimes(1)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(stream.mock.calls[1][3]).toBe(originalKey)
    wrapper.unmount()
  })

  it('renders fenced code as text, translates statuses and never executes model HTML', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([conversation])
    vi.spyOn(conversationApi, 'messages').mockResolvedValue([{
      id: '101', conversationId: '100', sequenceNo: 1, role: 'ASSISTANT', status: 'COMPLETED', evidence: [], createdAt: '',
      content: '代码示例：\n```java\nvoid run() {}\n```\n<img src=x onerror="alert(1)">',
    }])
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    expect(wrapper.get('.model-answer pre code').text()).toBe('void run() {}')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).not.toContain('COMPLETED')
    wrapper.unmount()
  })

  it('ignores a late history load after switching projects', async () => {
    let finish!: (messages: never[]) => void
    vi.spyOn(conversationApi, 'list').mockResolvedValueOnce([conversation]).mockResolvedValueOnce([])
    vi.spyOn(conversationApi, 'messages').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    await wrapper.setProps({ projectId: '20' })
    await flushPromises()
    finish([{ id: 'old', role: 'ASSISTANT', content: '旧项目私有回答', status: 'COMPLETED', evidence: [] }] as never[])
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧项目私有回答')
    wrapper.unmount()
  })

  it('recovers a persisted answer when the final SSE event is lost', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([conversation])
    vi.spyOn(conversationApi, 'messages').mockResolvedValueOnce([]).mockResolvedValueOnce([{
      id: '102', conversationId: '100', sequenceNo: 2, role: 'ASSISTANT', status: 'COMPLETED', evidence: [], createdAt: '',
      content: '已经保存的完整回答',
    }])
    vi.spyOn(conversationApi, 'streamMessage').mockImplementation(async (_p, _c, _q, _k, handlers) => {
      handlers.onMessage?.({ conversationId: '100', messageId: '102', status: 'RUNNING' })
      // Connection ends without a done event; persistence is the authority.
    })
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    await wrapper.get('textarea').setValue('核心流程？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.text()).toContain('已经保存的完整回答'))
    expect(wrapper.get('textarea').element.value).toBe('')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('restores server history on reopen and filters titles without calling a model', async () => {
    const other = { ...conversation, id: '200', title: '库存更新说明' }
    vi.spyOn(conversationApi, 'list').mockResolvedValue([conversation, other])
    const history = vi.spyOn(conversationApi, 'messages').mockResolvedValue([{
      id: '102', conversationId: '100', sequenceNo: 2, role: 'ASSISTANT', content: '已经保存在数据库的回答',
      status: 'COMPLETED', evidence: [], createdAt: '',
    }])
    const stream = vi.spyOn(conversationApi, 'streamMessage')
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    expect(wrapper.text()).toContain('已经保存在数据库的回答')
    await wrapper.get('input[type="search"]').setValue('库存')
    expect(wrapper.findAll('.conversation-list > button:not(.button)')).toHaveLength(1)
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(history).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已经保存在数据库的回答')
    expect(stream).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows partial SSE text while the connection is still open, then shows the saved answer once', async () => {
    vi.useFakeTimers()
    const answer = '先校验订单，再保存订单数据。'
    vi.spyOn(conversationApi, 'list').mockResolvedValue([conversation])
    vi.spyOn(conversationApi, 'messages').mockResolvedValueOnce([]).mockResolvedValueOnce([{
      id: '102', conversationId: '100', sequenceNo: 2, role: 'ASSISTANT', content: answer,
      status: 'COMPLETED', evidence: [], createdAt: '',
    }])
    let finish!: () => void
    vi.spyOn(conversationApi, 'streamMessage').mockImplementation(async (_p, _c, _q, _k, handlers) => {
      handlers.onToken?.({ content: answer })
      await new Promise<void>(resolve => { finish = resolve })
      handlers.onDone?.({ id: '102', content: answer } as never)
    })
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    await wrapper.get('textarea').setValue('订单怎么创建？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(16)
    const partial = wrapper.get('.streaming .model-answer').text()
    expect(partial.length).toBeGreaterThan(0)
    expect(partial.length).toBeLessThan(answer.length)
    expect(wrapper.text()).toContain('正在逐字输出')
    finish()
    await flushPromises()
    await vi.runAllTimersAsync()
    await flushPromises()
    expect(wrapper.find('.streaming').exists()).toBe(false)
    expect(wrapper.findAll('.assistant')).toHaveLength(1)
    expect(wrapper.text()).toContain(answer)
    wrapper.unmount()
  })

  it('prefills beginner questions and does not submit while composing Chinese or using Shift Enter', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const stream = vi.spyOn(conversationApi, 'streamMessage')
    const wrapper = mount(AgentConversationModal, { props: { open: true, projectId: '10' } })
    await flushPromises()
    await wrapper.get('.chat-suggestions button').trigger('click')
    expect(wrapper.get('textarea').element.value).toContain('有哪些功能')
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter', isComposing: true })
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(stream).not.toHaveBeenCalled()
    wrapper.unmount()
  })

})

