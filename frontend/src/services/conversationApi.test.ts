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
  it('handles CRLF and multi-byte Chinese characters split across network packets', async () => {
    const encoder = new TextEncoder()
    const events = encoder.encode('event: token\r\ndata: {"content":"订单✅"}\r\n\r\nevent: done\r\ndata: {"content":"订单✅"}\r\n\r\n')
    const body = new ReadableStream<Uint8Array>({ start(controller) {
      for (const byte of events) controller.enqueue(new Uint8Array([byte]))
      controller.close()
    } })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }))
    const onToken = vi.fn(), onDone = vi.fn()
    await conversationApi.streamMessage('1', '11', '流程？', 'test-key', { onToken, onDone })
    expect(onToken).toHaveBeenCalledExactlyOnceWith({ content: '订单✅' })
    expect(onDone).toHaveBeenCalledExactlyOnceWith({ content: '订单✅' })
  })

  it('rejects a successful HTTP response that is not an SSE answer', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>proxy</html>', { headers: { 'Content-Type': 'text/html' } }))
    await expect(conversationApi.streamMessage('1', '11', '流程？', 'test-key', {})).rejects.toThrow('未返回流式回答')
  })

  it('uses authenticated resource contracts for rename, delete, stop and an explicit model', async () => {
    setAuthSession({ accessToken: 'account-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', user: { id: '1', username: 'demo', role: 'USER' } })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ code: 0, data: null }), { headers: { 'Content-Type': 'application/json' } }))
    await conversationApi.rename('9007199254740993', '2', '接口说明')
    await conversationApi.delete('9007199254740993', '2')
    await conversationApi.cancel('9007199254740993', '2', '9007199254740995')
    await conversationApi.create('9007199254740993', undefined, { provider: 'COMPATIBLE', model: 'allowed-model' })
    expect(fetchMock.mock.calls[0]).toEqual(['/api/projects/9007199254740993/agent-conversations/2', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ title: '接口说明' }), headers: expect.objectContaining({ Authorization: 'Bearer account-token' }) })])
    expect(fetchMock.mock.calls[1][1]?.method).toBe('DELETE')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/projects/9007199254740993/agent-conversations/2/messages/9007199254740995/cancellations')
    expect(fetchMock.mock.calls[2][1]?.method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[3][1]?.body as string)).toEqual({ title: null, provider: 'COMPATIBLE', model: 'allowed-model' })
  })

})
