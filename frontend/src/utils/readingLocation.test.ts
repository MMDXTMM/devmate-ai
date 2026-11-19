import { describe, expect, it } from 'vitest'
import { parseReadingLocation, readReadingLocation, writeReadingLocation } from './readingLocation'
describe('stable reading locations', () => {
  it('round trips project, flow, revision and conversation without losing filters', () => {
    writeReadingLocation({ name: '测试项目', status: 'READY' })
    writeReadingLocation({ projectId: '2120450241570844673', revision: 'abc', item: 'flow:sign', conversation: '123', chat: '1' })
    expect(readReadingLocation()).toMatchObject({ projectId: '2120450241570844673', revision: 'abc', item: 'flow:sign', name: '测试项目', conversation: '123' })
    writeReadingLocation({ projectId: undefined })
    expect(readReadingLocation()).toMatchObject({ name: '测试项目', status: 'READY' })
    expect(readReadingLocation().conversation).toBeUndefined()
  })
  it('rejects arbitrary paths and oversized state', () => {
    expect(parseReadingLocation('#javascript:alert(1)')).toEqual({})
    expect(parseReadingLocation('#/projects/1?item=' + 'x'.repeat(200)).item).toBeUndefined()
  })
})
