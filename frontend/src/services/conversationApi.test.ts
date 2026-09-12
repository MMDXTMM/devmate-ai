import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAuthSession } from './authSession'
import { conversationApi } from './conversationApi'

describe('conversationApi SSE stream', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    sessionStorage.clear()
  })

  it('parses split named events and sends the account token', async () => {
    setAuthSession({
      accessToken: 'account-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      user: { id: '9007199254740993', username: 'demo', role: 'USER' },
    })
    const encoder = new TextEncoder()
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: message\ndata: {"conversationId":"11","messageId":"12","status":"RUNNING"}\n\nevent: token\nda'))
        controller.enqueue(encoder.encode('ta: {"content":"订单"}\n\nevent: evidence\ndata: [{"chunkId":"42","filePath":"OrderService.java","excerpt":"class OrderService {}"}]\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: {"id":"12","conversationId":"11","sequenceNo":2,"role":"ASSISTANT","content":"订单","status":"COMPLETED","evidence":[],"createdAt":"2026-08-17T00:00:00Z"}\n\n'))
        controller.close()
      },
    })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(body, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }))
    const onMessage = vi.fn()
    const onToken = vi.fn()
    const onEvidence = vi.fn()
    const onDone = vi.fn()

    await conversationApi.streamMessage('1', '11', '订单怎么创建？', '550e8400-e29b-41d4-a716-446655440000', {
      onMessage,
      onToken,
      onEvidence,
      onDone,
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/1/agent-conversations/11/messages/stream',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer account-token' }),
      }),
    )
    expect(onMessage).toHaveBeenCalledWith({ conversationId: '11', messageId: '12', status: 'RUNNING' })
    expect(onToken).toHaveBeenCalledWith({ content: '订单' })
    expect(onEvidence).toHaveBeenCalledWith([
      expect.objectContaining({ chunkId: '42', filePath: 'OrderService.java' }),
    ])
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ id: '12', content: '订单' }))
  })
})
