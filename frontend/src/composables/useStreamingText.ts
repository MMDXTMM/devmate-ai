import { onBeforeUnmount, ref } from 'vue'

/** Smooth incoming SSE chunks without waiting for the full model response. */
export function useStreamingText() {
  const text = ref('')
  let queue: string[] = []
  let timer: ReturnType<typeof setTimeout> | undefined
  let waiting: (() => void)[] = []
  const reducedMotion = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  function drained() { waiting.splice(0).forEach(resolve => resolve()) }
  function tick() {
    timer = undefined
    const count = reducedMotion() ? queue.length : Math.min(24, Math.max(1, Math.ceil(queue.length / 24)))
    text.value += queue.splice(0, count).join('')
    if (queue.length) timer = setTimeout(tick, 16)
    else drained()
  }
  function append(value: string) {
    queue.push(...Array.from(value))
    if (!timer) timer = setTimeout(tick, 16)
  }
  function reset() {
    if (timer) clearTimeout(timer)
    timer = undefined
    queue = []
    text.value = ''
    drained()
  }
  function finish(canonical?: string): Promise<void> {
    if (canonical !== undefined) {
      if (!canonical.startsWith(text.value)) {
        reset()
        text.value = canonical
        return Promise.resolve()
      }
      queue = Array.from(canonical.slice(text.value.length))
    }
    if (!queue.length) return Promise.resolve()
    if (!timer) timer = setTimeout(tick, 16)
    return new Promise(resolve => waiting.push(resolve))
  }
  onBeforeUnmount(reset)
  return { text, append, reset, finish }
}
