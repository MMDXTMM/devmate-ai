import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useStreamingText } from './useStreamingText'

describe('progressive SSE text', () => {
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
  function setup() {
    let reveal!: ReturnType<typeof useStreamingText>
    const wrapper = mount(defineComponent({ setup() { reveal = useStreamingText(); return () => reveal.text.value } }))
    return { reveal, wrapper }
  }
  it('reveals a burst before completion, preserving Unicode and the canonical saved answer', async () => {
    vi.useFakeTimers()
    const { reveal, wrapper } = setup()
    const answer = '订单✅由服务层创建，随后写入数据库。'
    reveal.append(answer)
    await vi.advanceTimersByTimeAsync(16)
    expect(reveal.text.value.length).toBeGreaterThan(0)
    expect(reveal.text.value.length).toBeLessThan(answer.length)
    const finished = reveal.finish(answer)
    await vi.runAllTimersAsync()
    await finished
    expect(wrapper.text()).toBe(answer)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('cancels queued output on project change or unmount', async () => {
    vi.useFakeTimers()
    const { reveal, wrapper } = setup()
    reveal.append('旧项目内容')
    const finished = reveal.finish()
    reveal.reset()
    await finished
    reveal.append('新项目')
    await vi.runAllTimersAsync()
    expect(reveal.text.value).toBe('新项目')
    reveal.append('关闭后不能继续')
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('respects reduced motion without dropping any SSE content', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const { reveal, wrapper } = setup()
    reveal.append('无动画的完整增量')
    await vi.advanceTimersByTimeAsync(16)
    expect(wrapper.text()).toBe('无动画的完整增量')
    wrapper.unmount()
  })
})
