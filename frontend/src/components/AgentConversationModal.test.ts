import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AgentConversationModal from './AgentConversationModal.vue'
import { conversationApi } from '../services/conversationApi'

describe('AgentConversationModal', () => {
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
    expect(wrapper.text()).toContain('由 OrderService 创建。')
    expect(wrapper.text()).toContain('OrderService.java')
    expect(wrapper.text()).toContain('代码证据')
  })
})

