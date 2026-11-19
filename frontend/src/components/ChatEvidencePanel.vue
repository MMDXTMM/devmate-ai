<script setup lang="ts">
import type { ConversationEvidence } from '../types/conversation'
import EvidenceCode from './EvidenceCode.vue'
import { nextTick, watch, ref } from 'vue'
import { sourceLabel } from '../utils/sourcePresentation'
const props = defineProps<{ evidence: ConversationEvidence[]; title: string; projectId?: string; revision?: string; focusIndex?: number }>()
const panel = ref<HTMLElement>()
watch(() => [props.focusIndex, props.evidence] as const, async () => {
  await nextTick()
  const target = panel.value?.querySelectorAll<HTMLElement>('.chat-evidence')[props.focusIndex ?? 0]
  target?.focus()
  target?.scrollIntoView?.({ block: 'nearest' })
}, { immediate: true })
defineEmits<{ close: [] }>()
</script>
<template>
  <aside ref="panel" class="chat-evidence-panel" aria-label="本轮代码证据">
    <header><div><small>回答依据</small><h3>代码证据 · {{ evidence.length }}</h3></div><button type="button" class="icon-button" aria-label="收起代码证据" @click="$emit('close')">×</button></header>
    <p class="evidence-question">{{ title }}</p>
    <div class="evidence-scroll">
      <article v-for="(item, index) in evidence" :key="`${item.chunkId}:${item.startLine}`" class="chat-evidence" tabindex="-1"><span class="evidence-number">证据 {{ index + 1 }}</span><b>{{ item.filePath }}</b><small>{{ item.symbolName }} · 第 {{ item.startLine }}–{{ item.endLine }} 行</small><small>{{ sourceLabel(item.filePath) }}</small><EvidenceCode :project-id="projectId" :revision="revision" :chunk-id="item.chunkId" :file-path="item.filePath" :code="item.excerpt" :truncated="true" /></article>
      <p v-if="!evidence.length">本轮尚未找到可展示的代码证据。模型结论仍需核对。</p>
    </div>
  </aside>
</template>
