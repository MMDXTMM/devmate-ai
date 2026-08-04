<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { RetrievalSearch, RetrievalTrimReason } from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()
const form = reactive({ query: '', topK: 8, tokenBudget: 4000 })
const result = ref<RetrievalSearch | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const mode = ref<'project' | 'diff'>('project')

const budgetRate = computed(() => {
  if (!result.value?.tokenBudget) return 0
  return Math.round(result.value.usedTokens / result.value.tokenBudget * 100)
})

const trimLabel: Record<RetrievalTrimReason, string> = {
  DUPLICATE_CONTENT: '内容重复',
  TOKEN_BUDGET: '超出 Token 预算',
  TOP_K: '超出 Top-K',
}

async function search(targetMode: 'project' | 'diff') {
  if (!props.projectId) return
  if (targetMode === 'project' && !form.query.trim()) {
    errorMessage.value = '请输入需要检索的代码或工程问题'
    return
  }
  mode.value = targetMode
  loading.value = true
  errorMessage.value = ''
  result.value = null
  const payload = {
    query: form.query.trim(),
    topK: form.topK,
    tokenBudget: form.tokenBudget,
  }
  try {
    result.value = targetMode === 'project'
      ? await projectApi.searchRetrieval(props.projectId, payload)
      : await projectApi.retrieveLatestDiffContext(props.projectId, payload)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '上下文检索失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (!open) return
    result.value = null
    errorMessage.value = ''
    mode.value = 'project'
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal retrieval-modal" role="dialog" aria-modal="true" aria-labelledby="retrieval-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">CHANGE-AWARE CONTEXT</p>
          <h2 id="retrieval-title">{{ projectName }} · 上下文检索</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <form class="retrieval-form" @submit.prevent="search('project')">
        <label>
          <span>检索问题</span>
          <textarea
            v-model="form.query"
            maxlength="500"
            rows="3"
            placeholder="例如：库存扣减的事务边界和数据库写入在哪里？"
          ></textarea>
        </label>
        <div class="retrieval-controls">
          <label><span>Top-K</span><input v-model.number="form.topK" type="number" min="1" max="20" /></label>
          <label><span>Token 预算</span><input v-model.number="form.tokenBudget" type="number" min="100" max="12000" step="100" /></label>
          <button class="button secondary" type="button" :disabled="loading" @click="search('diff')">围绕最新 Diff</button>
          <button class="button primary" type="submit" :disabled="loading">检索项目</button>
        </div>
        <small>“围绕最新 Diff”会把变更方法作为种子，并扩展其调用方、被调用方、配置和数据库关系。</small>
      </form>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="loading" class="source-loading">正在执行项目/版本隔离的混合检索与预算裁剪…</div>
      <template v-else-if="result">
        <div class="retrieval-summary">
          <span><small>模式</small><b>{{ mode === 'diff' ? 'Diff 上下文' : '项目检索' }}</b></span>
          <span><small>候选</small><b>{{ result.candidateCount }}</b></span>
          <span><small>采用</small><b>{{ result.selectedCount }} / {{ result.topK }}</b></span>
          <span><small>预算</small><b>{{ result.usedTokens }} / {{ result.tokenBudget }}（{{ budgetRate }}%）</b></span>
        </div>
        <div v-if="result.candidateLimitReached" class="notice warning">
          候选数量达到扫描上限，本次结果不能代表完整索引；后续向量召回将减少全量扫描。
        </div>
        <div v-if="result.referenceLimitReached" class="notice warning">
          变更种子的关系数量达到扩展上限，本次调用关系上下文可能不完整。
        </div>

        <div v-if="!result.hits.length" class="state-box compact">
          <h3>没有命中可用上下文</h3>
          <p>可以换用类名、方法名、配置键或数据库表名；中文语义召回将在 Embedding 阶段增强。</p>
        </div>
        <div v-else class="retrieval-results">
          <article v-for="hit in result.hits" :key="hit.chunkId" class="retrieval-hit">
            <div class="retrieval-hit-head">
              <span class="symbol-type">{{ hit.chunkType }}</span>
              <code>{{ hit.symbolName || hit.filePath }}</code>
              <b>{{ hit.score.toFixed(3) }}</b>
            </div>
            <small>{{ hit.filePath }}<template v-if="hit.startLine">:{{ hit.startLine }}–{{ hit.endLine }}</template></small>
            <div class="retrieval-reasons"><span v-for="reason in hit.reasons" :key="reason">{{ reason }}</span></div>
            <pre>{{ hit.excerpt }}</pre>
          </article>

          <details v-if="result.trimmedCount" class="retrieval-trimmed">
            <summary>查看 {{ result.trimmedCount }} 个未采用候选</summary>
            <ul>
              <li v-for="item in result.trimmed" :key="`${item.chunkId}-${item.reason}`">
                <code>{{ item.symbolName || item.filePath }}</code>
                <span>{{ trimLabel[item.reason] }} · {{ item.estimatedTokens }} tokens</span>
              </li>
            </ul>
            <small v-if="result.omittedTrimmedDetails">另有 {{ result.omittedTrimmedDetails }} 条明细未在响应中展开。</small>
          </details>
        </div>
      </template>
    </section>
  </div>
</template>
