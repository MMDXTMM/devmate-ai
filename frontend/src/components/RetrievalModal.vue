<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type {
  RetrievalHit,
  RetrievalMode,
  RetrievalSearch,
  RetrievalTrimReason,
  SourceSymbolType,
} from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
  initialQuery?: string
}>()

const emit = defineEmits<{ close: [] }>()
const form = reactive<{ query: string; topK: number; tokenBudget: number; retrievalMode: RetrievalMode }>({
  query: '', topK: 8, tokenBudget: 4000, retrievalMode: 'HYBRID',
})
const result = ref<RetrievalSearch | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const mode = ref<'project' | 'diff'>('project')
const copiedChunkId = ref('')

const budgetRate = computed(() => {
  if (!result.value?.tokenBudget) return 0
  return Math.round(result.value.usedTokens / result.value.tokenBudget * 100)
})

const trimLabel: Record<RetrievalTrimReason, string> = {
  DUPLICATE_CONTENT: '内容重复',
  TOKEN_BUDGET: '超出 Token 预算',
  TOP_K: '超出 Top-K',
}

const retrievalModeLabels: Record<RetrievalSearch['executedMode'], string> = {
  LEXICAL: '关键词检索',
  VECTOR: '语义检索',
  HYBRID: '关键词 + 语义混合检索',
  LEXICAL_FALLBACK: '关键词检索（语义检索已降级）',
}

const sourceKindLabels: Record<RetrievalHit['sourceKind'], string> = {
  SOURCE_CODE: 'Java 源码',
  CONFIGURATION: '项目配置',
  DATABASE_SCHEMA: '数据库结构',
}

const chunkTypeLabels: Record<SourceSymbolType, string> = {
  FILE_HEADER: '文件头与导入',
  IMPORT: '导入语句',
  CLASS: '类或接口',
  CONSTRUCTOR: '构造方法',
  METHOD: '方法实现',
  CONFIG_PROPERTY: '配置项',
  DATABASE_TABLE: '数据库表',
  DATABASE_COLUMN: '数据库字段',
  DATABASE_INDEX: '数据库索引',
  DATABASE_CONSTRAINT: '数据库约束',
  DATABASE_CHANGE: '数据库变更',
}

const reasonLabels: Record<string, string> = {
  EXACT_SYMBOL: '符号名完全匹配',
  EXACT_PATH: '文件路径完全匹配',
  EXACT_CONTENT: '代码内容完全匹配',
  SYMBOL_TERM: '符号名关键词匹配',
  PATH_TERM: '文件路径关键词匹配',
  CONTENT_TERM: '代码关键词匹配',
  CONTENT_SUBSTRING: '代码片段匹配',
  DIFF_SYMBOL: '最新变更符号',
  LEXICAL_RANK: '关键词排序命中',
  DETERMINISTIC_CONTEXT: '调用关系上下文',
  VECTOR_SIMILARITY: '语义相似',
}

const referenceReasonLabels: Record<string, string> = {
  METHOD_CALL: '方法调用关系',
  DATA_ACCESS: '数据访问关系',
  CONFIG_KEY: '配置键关系',
  CONFIG_PREFIX: '配置前缀关系',
  DATABASE_TABLE: '数据库表关系',
}

function reasonLabel(reason: string) {
  if (reasonLabels[reason]) return reasonLabels[reason]
  if (reason.startsWith('COSINE_')) return `向量相似度 ${reason.slice('COSINE_'.length)}`
  if (reason.startsWith('OUTGOING_')) {
    return `当前代码调用了相关${referenceReasonLabels[reason.slice('OUTGOING_'.length)] ?? '代码'}`
  }
  if (reason.startsWith('INCOMING_')) {
    return `相关${referenceReasonLabels[reason.slice('INCOMING_'.length)] ?? '代码'}调用了当前代码`
  }
  return '相关代码证据'
}

function hitExplanation(hit: RetrievalHit) {
  const explanations: Record<SourceSymbolType, string> = {
    FILE_HEADER: '先从包声明和依赖导入判断该文件所属模块及依赖方向。',
    IMPORT: '该导入项说明当前文件依赖了对应类型或工具。',
    CLASS: '这是类或接口定义，建议先看注解、继承关系和公开方法，再进入具体实现。',
    CONSTRUCTOR: '这是对象创建入口，构造参数反映组件运行所需依赖。',
    METHOD: '这是方法实现，重点查看参数、状态变化、外部调用与返回值。',
    CONFIG_PROPERTY: '这是影响运行行为的配置项，需要结合读取该配置的 Java 代码理解。',
    DATABASE_TABLE: '这是业务数据对应的表定义，可用于确认持久化边界。',
    DATABASE_COLUMN: '这是表字段定义，可用于确认数据类型、必填约束和字段职责。',
    DATABASE_INDEX: '这是查询索引，说明系统重点优化了对应查询条件。',
    DATABASE_CONSTRAINT: '这是数据约束，用于保证字段取值或表之间关系正确。',
    DATABASE_CHANGE: '这是数据库迁移变更，需要按迁移版本理解表结构演进。',
  }
  return explanations[hit.chunkType]
}

async function copyExcerpt(hit: RetrievalHit) {
  try {
    await navigator.clipboard.writeText(hit.excerpt)
    copiedChunkId.value = hit.chunkId
    window.setTimeout(() => {
      if (copiedChunkId.value === hit.chunkId) copiedChunkId.value = ''
    }, 1500)
  } catch {
    errorMessage.value = '复制失败，请手动选择代码'
  }
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
    retrievalMode: form.retrievalMode,
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
    form.query = props.initialQuery ?? ''
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal retrieval-modal" role="dialog" aria-modal="true" aria-labelledby="retrieval-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">向项目代码提问</p>
          <h2 id="retrieval-title">向 {{ projectName }} 提问</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <form class="retrieval-form" @submit.prevent="search('project')">
        <label>
          <span>你想理解什么？</span>
          <textarea
            v-model="form.query"
            maxlength="500"
            rows="3"
            placeholder="例如：用户登录请求从哪个 Controller 进入，之后调用了哪些 Service？"
          ></textarea>
        </label>
        <div class="retrieval-submit-row">
          <small>结果会给出真实文件、类、方法和行号，便于继续阅读源码。</small>
          <button class="button primary" type="submit" :disabled="loading">查找代码线索</button>
        </div>
        <details class="retrieval-advanced">
          <summary>高级检索设置</summary>
          <div class="retrieval-controls">
            <label><span>检索策略</span><select v-model="form.retrievalMode" aria-label="检索策略">
              <option value="HYBRID">混合检索</option>
              <option value="LEXICAL">关键词基线</option>
              <option value="VECTOR">向量优先</option>
            </select></label>
            <label><span>结果数量</span><input v-model.number="form.topK" type="number" min="1" max="20" /></label>
            <label><span>上下文预算</span><input v-model.number="form.tokenBudget" type="number" min="100" max="12000" step="100" /></label>
            <button class="button secondary" type="button" :disabled="loading" @click="search('diff')">围绕最新变更检索</button>
          </div>
        </details>
      </form>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="loading" class="source-loading">正在项目代码中查找相关结构和调用关系…</div>
      <template v-else-if="result">
        <div class="retrieval-summary">
          <span><small>模式</small><b>{{ retrievalModeLabels[result.executedMode] }}</b></span>
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
        <div v-if="result.vectorLimitReached" class="notice warning">
          向量数量达到开发型存储扫描上限，后续需要迁移到专用 ANN 向量检索。
        </div>
        <div v-if="result.degradationReason" class="notice warning">
          向量检索已降级：{{ result.degradationReason }}
        </div>

        <div v-if="!result.hits.length" class="state-box compact">
          <h3>没有命中可用上下文</h3>
          <p>可以换用类名、方法名、配置键或数据库表名；中文语义召回将在 Embedding 阶段增强。</p>
        </div>
        <div v-else class="retrieval-results">
          <article v-for="hit in result.hits" :key="hit.chunkId" class="retrieval-hit">
            <div class="retrieval-hit-head">
              <span class="symbol-type">{{ chunkTypeLabels[hit.chunkType] }}</span>
              <code>{{ hit.symbolName || hit.filePath }}</code>
              <b>{{ hit.score.toFixed(3) }}</b>
            </div>
            <small>
              {{ sourceKindLabels[hit.sourceKind] }} · {{ hit.filePath }}
              <template v-if="hit.startLine"> · 第 {{ hit.startLine }}-{{ hit.endLine }} 行</template>
            </small>
            <p class="retrieval-explanation"><b>代码解读：</b>{{ hitExplanation(hit) }}</p>
            <div class="retrieval-reasons">
              <span v-for="reason in hit.reasons" :key="reason">{{ reasonLabel(reason) }}</span>
            </div>
            <div class="code-block-head">
              <span>真实代码片段</span>
              <button class="button secondary copy-code-button" type="button" @click="copyExcerpt(hit)">
                {{ copiedChunkId === hit.chunkId ? '已复制' : '复制代码' }}
              </button>
            </div>
            <pre><code>{{ hit.excerpt }}</code></pre>
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
