<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ code: string; language?: string }>()
const copyState = ref('复制代码')
watch(() => props.code, () => { copyState.value = '复制代码' })
async function copy() {
  try { await navigator.clipboard.writeText(props.code); copyState.value = '已复制' }
  catch { copyState.value = '复制失败，请手动选择' }
}
</script>
<template>
  <div class="answer-code">
    <header><small>{{ language || 'code' }}</small><button type="button" @click="copy">{{ copyState }}</button></header>
    <pre><code>{{ code.trimEnd() }}</code></pre>
  </div>
</template>
