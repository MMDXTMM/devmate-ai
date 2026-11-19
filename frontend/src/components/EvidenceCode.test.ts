import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import EvidenceCode from './EvidenceCode.vue'
import { projectApi } from '../services/projectApi'
describe('same revision source drill down', () => {
  it('reads full Python evidence on demand and continues using a bounded offset', async () => {
    const read = vi.spyOn(projectApi, 'getSourceChunkDetail').mockResolvedValue({ id: '42', documentId: '10', chunkType: 'TEXT_EXCERPT', symbolName: 'signer', annotations: [], revision: 'abc', startLine: 200, endLine: 240, code: 'def sign(value): return value', originalCharacters: 20000, truncated: true, nextOffset: 16000 })
    vi.spyOn(projectApi, 'listSourceSymbols').mockResolvedValue([])
    const wrapper = mount(EvidenceCode, { props: { projectId: '1', revision: 'abc', chunkId: '42', filePath: 'signer.py', code: 'preview…', truncated: true } })
    expect(read).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('python')
    await wrapper.get('.button').trigger('click')
    await flushPromises()
    expect(read).toHaveBeenCalledWith('1', '42', 0)
    expect(wrapper.get('pre').text()).toContain('def sign')
    read.mockResolvedValueOnce({ ...(await read.mock.results[0]!.value), code: 'return output', truncated: false, nextOffset: null })
    await wrapper.findAll('button').find(item => item.text().includes('继续读取'))!.trigger('click')
    await flushPromises()
    expect(read).toHaveBeenLastCalledWith('1', '42', 16000)
    wrapper.unmount()
  })
  it('rejects a different revision without showing its source', async () => {
    vi.spyOn(projectApi, 'getSourceChunkDetail').mockResolvedValue({ revision: 'other', code: 'wrong revision' } as never)
    const wrapper = mount(EvidenceCode, { props: { projectId: '1', revision: 'abc', chunkId: '42', filePath: 'a.py', code: 'original' } })
    await wrapper.get('.button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('版本不一致')
    expect(wrapper.text()).not.toContain('wrong revision')
    wrapper.unmount()
  })
})
