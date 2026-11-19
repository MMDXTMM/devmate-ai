import { nextTick, onBeforeUnmount, ref, watch, type Ref, type ComponentPublicInstance } from 'vue'

/** Shared keyboard behavior for cancellable dialogs, including focus restoration. */
export function useModalFocus(open: () => boolean, close: () => void) {
  const dialog: Ref<HTMLElement | null> = ref(null)
  let opener: HTMLElement | null = null
  let generation = 0
  const focusable = () => Array.from(dialog.value?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary, a[href], [tabindex="0"]',
  ) ?? []).filter(item => !item.closest('[hidden], [inert]'))
  function keydown(event: KeyboardEvent) {
    if (!open() || !dialog.value) return
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      close()
    } else if (event.key === 'Tab') {
      const items = focusable()
      const first = items[0]
      const last = items.at(-1)
      if (!first) { event.preventDefault(); dialog.value.focus(); return }
      if (!dialog.value.contains(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
        event.preventDefault(); (event.shiftKey ? last : first)?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus()
      }
    }
  }
  function release() {
    document.removeEventListener('keydown', keydown)
    if (opener?.isConnected) opener.focus()
    opener = null
  }
  watch(open, async value => {
    const ticket = ++generation
    if (!value) { release(); return }
    opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    if (ticket !== generation || !open()) return
    document.addEventListener('keydown', keydown)
    ;(dialog.value?.querySelector<HTMLElement>('input, textarea') ?? focusable()[0] ?? dialog.value)?.focus()
  }, { immediate: true })
  onBeforeUnmount(() => { generation++; release() })
  return (element: Element | ComponentPublicInstance | null) => { dialog.value = element instanceof HTMLElement ? element : null }
}
