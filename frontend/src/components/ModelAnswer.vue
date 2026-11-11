<script setup lang="ts">
import { computed } from 'vue'
import ModelAnswerInline from './ModelAnswerInline.vue'
const props = defineProps<{ content: string }>()
type Block = { kind: 'code' | 'heading' | 'paragraph' | 'ul' | 'ol'; text: string; language?: string; items?: string[] }
// A small text-only Markdown subset. No model HTML, URL or arbitrary tag is executed.
const blocks = computed(() => {
  const result: Block[] = []
  let code: Block | undefined
  for (const line of props.content.split('\n')) {
    const fence = line.match(/^```([^\s]*)\s*$/)
    if (fence) {
      if (code) { result.push(code); code = undefined }
      else code = { kind: 'code', text: '', language: fence[1] }
      continue
    }
    if (code) { code.text += (code.text ? '\n' : '') + line; continue }
    if (!line.trim()) { result.push({ kind: 'paragraph', text: '' }); continue }
    const heading = line.match(/^#{1,6}\s+(.+)$/)
    if (heading) { result.push({ kind: 'heading', text: heading[1] }); continue }
    const list = line.match(/^\s*(?:([-*])\s+|\d+[.)]\s+)(.*)$/)
    if (list) {
      const kind = list[1] ? 'ul' : 'ol'
      const last = result.at(-1)
      if (last?.kind === kind) last.items!.push(list[2])
      else result.push({ kind, text: '', items: [list[2]] })
      continue
    }
    const last = result.at(-1)
    if (last?.kind === 'paragraph' && last.text) last.text += '\n' + line
    else result.push({ kind: 'paragraph', text: line })
  }
  if (code) result.push(code)
  return result.filter(block => block.kind === 'code' || block.text || block.items?.length)
})
</script>

<template>
  <div class="model-answer">
    <template v-for="(block, index) in blocks" :key="index">
      <div v-if="block.kind === 'code'" class="answer-code"><small v-if="block.language">{{ block.language }}</small><pre><code>{{ block.text.trimEnd() }}</code></pre></div>
      <h3 v-else-if="block.kind === 'heading'"><ModelAnswerInline :text="block.text" /></h3>
      <component v-else-if="block.kind === 'ul' || block.kind === 'ol'" :is="block.kind"><li v-for="(item, i) in block.items" :key="i"><ModelAnswerInline :text="item" /></li></component>
      <p v-else><ModelAnswerInline :text="block.text" /></p>
    </template>
  </div>
</template>
