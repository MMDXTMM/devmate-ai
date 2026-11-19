<script setup lang="ts">
import { ref, watch } from 'vue'
import { projectApi } from '../services/projectApi'
import type { SourceSymbol, SourceSymbolDetail } from '../types/project'
import { sourceLanguage } from '../utils/sourcePresentation'
import AnswerCodeBlock from './AnswerCodeBlock.vue'
const props = defineProps<{ projectId?: string; revision?: string; chunkId: string; filePath: string; code: string; truncated?: boolean }>()
const detail = ref<SourceSymbolDetail>()
const following = ref<SourceSymbol>()
const loading = ref(false)
const error = ref('')
let generation = 0
watch(() => [props.chunkId, props.projectId, props.revision], () => {
  generation++; detail.value = undefined; following.value = undefined; error.value = ''; loading.value = false
})
async function read(next = false, offset = 0) {
  if (!props.projectId || !props.revision || loading.value) return
  const ticket = generation
  loading.value = true; error.value = ''
  try {
    const id = next ? following.value?.id : detail.value?.id ?? props.chunkId
    if (!id) return
    const value = await projectApi.getSourceChunkDetail(props.projectId, id, offset)
    if (ticket !== generation) return
    if (value.revision !== props.revision) throw new Error('源码版本不一致，已停止读取；不会混用其他版本')
    const symbols = await projectApi.listSourceSymbols(props.projectId, value.documentId)
    if (ticket !== generation) return
    if (value.revision !== props.revision) throw new Error('证据版本不一致，已停止读取')
    detail.value = value
    following.value = symbols.filter(item => item.revision === props.revision && item.startLine > value.endLine)
      .sort((a, b) => a.startLine - b.startLine)[0]
  } catch (cause) {
    if (ticket === generation) error.value = cause instanceof Error ? cause.message : '源码读取失败，请重试'
  } finally { if (ticket === generation) loading.value = false }
}
</script>
<template>
  <div class="evidence-code">
    <p v-if="detail">当前片段：第 {{ detail.startLine }}–{{ detail.endLine }} 行</p>
    <AnswerCodeBlock :code="detail?.code ?? code" :language="sourceLanguage(filePath)" />
    <small v-if="!detail && truncated">这是有限预览，可读取同一版本的完整结构片段。</small>
    <button v-if="detail?.nextOffset != null" type="button" class="button secondary" :disabled="loading" @click="read(false, detail.nextOffset)">继续读取当前片段</button>
    <button v-if="projectId && revision && !detail" type="button" class="button secondary" :disabled="loading" @click="read()">{{ loading ? '正在读取…' : '读取完整片段' }}</button>
    <button v-if="following && detail?.nextOffset == null" type="button" class="button secondary" :disabled="loading" @click="read(true)">继续读下一段 · 第 {{ following.startLine }} 行</button>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>
