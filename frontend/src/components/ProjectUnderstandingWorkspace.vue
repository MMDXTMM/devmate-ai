<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { Project, ReviewWorkflow, SourceDocument, SourceReference } from '../types/project'

const props = defineProps<{
  project: Project
  parsing: boolean
  rebuilding: boolean
  deleting: boolean
  reviewing: boolean
  reviewWorkflow?: ReviewWorkflow
}>()

const emit = defineEmits<{
  back: []
  structure: []
  search: [query: string]
  reparse: []
  rebuild: []
  diff: []
  staticAnalysis: []
  review: []
  runReview: []
  evaluation: []
  edit: []
  delete: []
}>()

const documents = ref<SourceDocument[]>([])
const references = ref<SourceReference[]>([])
const loading = ref(false)
const errorMessage = ref('')

const sourceFiles = computed(() => documents.value.filter((item) => item.sourceKind === 'SOURCE_CODE'))
const configurationFiles = computed(() => documents.value.filter((item) => item.sourceKind === 'CONFIGURATION'))
const databaseFiles = computed(() => documents.value.filter((item) => item.sourceKind === 'DATABASE_SCHEMA'))
const totalSymbols = computed(() => documents.value.reduce((total, item) => total + item.chunkCount, 0))
const resolvedReferences = computed(() => references.value.filter((item) => item.resolved).length)
const packages = computed(() => {
  const values = sourceFiles.value
    .map((item) => item.packageName)
    .filter((item): item is string => Boolean(item))
  return new Set(values).size
})
const recentDocuments = computed(() => documents.value.slice(0, 6))
const reviewStages = computed(() => [
  { key: 'SOURCE_IMPORT', label: '源码解析', completed: Boolean(props.reviewWorkflow?.indexTaskId) },
  { key: 'DIFF', label: '变更定位', completed: Boolean(props.reviewWorkflow?.reviewTaskId) },
  { key: 'STATIC_ANALYSIS', label: '静态检查', completed: Boolean(props.reviewWorkflow?.staticAnalysisTaskId) },
  { key: 'EMBEDDING', label: 'RAG 索引', completed: Boolean(props.reviewWorkflow?.embeddingTaskId) },
  { key: 'AGENT_REVIEW', label: 'Agent 审查', completed: Boolean(props.reviewWorkflow?.aiReviewTaskId) },
])

function reviewStageState(stage: { key: string; completed: boolean }) {
  if (stage.completed) return 'completed'
  if (props.reviewWorkflow?.status === 'FAILED'
      && props.reviewWorkflow.currentStage === stage.key) return 'failed'
  if (props.reviewing && props.reviewWorkflow?.currentStage === stage.key) return 'running'
  return 'pending'
}

function sourceKindLabel(kind: SourceDocument['sourceKind']) {
  return {
    SOURCE_CODE: 'Java 源码',
    CONFIGURATION: '配置',
    DATABASE_SCHEMA: '数据库结构',
  }[kind]
}

function formatDate(value?: string) {
  if (!value) return '尚未解析'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function loadOverview() {
  if (props.project.status !== 'READY') return
  loading.value = true
  errorMessage.value = ''
  try {
    const [loadedDocuments, loadedReferences] = await Promise.all([
      projectApi.listSourceDocuments(props.project.id),
      projectApi.listSourceReferences(props.project.id),
    ])
    documents.value = loadedDocuments
    references.value = loadedReferences
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '项目概览加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.project.id, props.project.currentRevision, props.project.status] as const,
  () => void loadOverview(),
  { immediate: true },
)
</script>

<template>
  <section class="understanding-workspace" aria-labelledby="workspace-title">
    <header class="workspace-header">
      <div>
        <button class="back-button" type="button" @click="emit('back')">← 返回项目</button>
        <p class="eyebrow">PROJECT UNDERSTANDING</p>
        <h1 id="workspace-title">{{ project.name }}</h1>
        <p>{{ project.description || '已解析的 Java 项目' }}</p>
      </div>
      <div class="workspace-status">
        <span class="status ready"><i></i>已可理解</span>
        <small>最近解析 {{ formatDate(project.lastIndexedAt) }}</small>
      </div>
    </header>

    <div v-if="errorMessage" class="notice error" role="alert">
      {{ errorMessage }}
      <button type="button" @click="loadOverview">重试</button>
    </div>

    <div v-if="loading" class="workspace-loading"><span class="loader"></span>正在整理项目结构…</div>
    <template v-else>
      <section class="understanding-summary" aria-label="解析结果">
        <div><strong>{{ sourceFiles.length }}</strong><span>Java 文件</span></div>
        <div><strong>{{ totalSymbols }}</strong><span>类与方法等结构</span></div>
        <div><strong>{{ packages }}</strong><span>源码包</span></div>
        <div><strong>{{ resolvedReferences }}</strong><span>已解析调用关系</span></div>
      </section>

      <section class="workspace-start">
        <div class="workspace-section-heading">
          <div>
            <p class="eyebrow">START HERE</p>
            <h2>从这里理解项目</h2>
          </div>
          <p>先看全局结构，再带着具体问题检索代码证据。</p>
        </div>

        <div class="workspace-primary-actions">
          <button class="review-primary-action" type="button" :disabled="reviewing" @click="emit('runReview')">
            <span class="action-index">01</span>
            <b>{{ reviewing ? '正在执行完整审查…' : '开始代码审查' }}</b>
            <small>自动完成变更定位、静态检查、RAG 取证和 Agent 审查</small>
            <i>→</i>
          </button>
          <button type="button" @click="emit('structure')">
            <span class="action-index">02</span>
            <b>浏览代码结构</b>
            <small>查看文件、类、方法、注解和调用关系</small>
            <i>→</i>
          </button>
          <button type="button" @click="emit('search', '')">
            <span class="action-index">03</span>
            <b>向项目提问</b>
            <small>搜索业务入口、调用链、配置和数据库关系</small>
            <i>→</i>
          </button>
        </div>

        <div v-if="reviewing || reviewWorkflow" class="review-workflow-status" :class="reviewWorkflow?.status.toLowerCase()">
          <div class="review-workflow-heading">
            <div>
              <b v-if="reviewing">审查任务正在运行</b>
              <b v-else-if="reviewWorkflow?.status === 'SUCCEEDED'">最近一次完整审查已完成</b>
              <b v-else>最近一次完整审查未完成</b>
              <small>系统按固定顺序执行，失败后不会继续消耗后续模型请求。</small>
            </div>
            <span>{{ reviewWorkflow?.status === 'SUCCEEDED' ? '完成' : reviewWorkflow?.status === 'FAILED' ? '失败' : '运行中' }}</span>
          </div>
          <ol>
            <li v-for="stage in reviewStages" :key="stage.key" :class="reviewStageState(stage)">
              <i></i><span>{{ stage.label }}</span>
            </li>
          </ol>
          <p v-if="reviewWorkflow?.status === 'FAILED'" role="alert">
            {{ reviewWorkflow.errorMessage }}<br />
            <strong>下一步：</strong>{{ reviewWorkflow.recoveryAction }}
          </p>
        </div>
      </section>

      <section class="workspace-content-grid">
        <div class="workspace-files">
          <div class="workspace-section-heading compact-heading">
            <div><p class="eyebrow">PARSED CONTENT</p><h2>已识别内容</h2></div>
          </div>
          <dl class="content-breakdown">
            <div><dt>源码</dt><dd>{{ sourceFiles.length }} 个文件</dd></div>
            <div><dt>配置</dt><dd>{{ configurationFiles.length }} 个文件</dd></div>
            <div><dt>数据库</dt><dd>{{ databaseFiles.length }} 个迁移文件</dd></div>
            <div><dt>版本</dt><dd>{{ project.currentRevision?.slice(0, 10) || '—' }}</dd></div>
          </dl>
          <ul v-if="recentDocuments.length" class="workspace-file-list">
            <li v-for="document in recentDocuments" :key="document.id">
              <span>{{ sourceKindLabel(document.sourceKind) }}</span>
              <div><b>{{ document.fileName }}</b><small>{{ document.filePath }}</small></div>
              <em>{{ document.chunkCount }}</em>
            </li>
          </ul>
          <p v-else class="workspace-empty">还没有可展示的结构化文件，请重新解析项目。</p>
        </div>

        <div class="workspace-guide">
          <div class="workspace-section-heading compact-heading">
            <div><p class="eyebrow">LEARNING PATH</p><h2>推荐理解顺序</h2></div>
          </div>
          <ol>
            <li><span>1</span><div><b>找到请求入口</b><small>先定位 Controller、接口路径和请求对象。</small></div></li>
            <li><span>2</span><div><b>跟进核心业务</b><small>沿 Service 方法和调用关系理解状态变化。</small></div></li>
            <li><span>3</span><div><b>核对数据与配置</b><small>查看 Mapper、数据库迁移和配置项如何支撑业务。</small></div></li>
            <li><span>4</span><div><b>再开始修改</b><small>检索相关测试和影响范围，形成最小开发方案。</small></div></li>
          </ol>
          <div class="question-examples">
            <span>可以先问</span>
            <button type="button" @click="emit('search', 'Controller Service 项目的主要请求入口和核心业务流程在哪里？')">主要业务流程在哪里？</button>
            <button type="button" @click="emit('search', 'Mapper Repository DATABASE_TABLE 数据访问和数据库表关系在哪里？')">数据如何持久化？</button>
            <button type="button" @click="emit('search', 'test 测试 修改功能应该从哪些文件开始？')">修改功能从哪里开始？</button>
          </div>
        </div>
      </section>

      <details class="advanced-analysis">
        <summary>
          <span><b>高级分析</b><small>代码审查、变更分析和评测按需使用</small></span>
          <i>⌄</i>
        </summary>
        <div class="advanced-actions">
          <button type="button" @click="emit('diff')"><b>变更范围</b><small>查看最新 Git Diff 覆盖</small></button>
          <button type="button" @click="emit('staticAnalysis')"><b>静态检查</b><small>检查确定性代码问题</small></button>
          <button type="button" @click="emit('review')"><b>最近审查报告</b><small>读取已保存的结构化结果</small></button>
          <button type="button" @click="emit('evaluation')"><b>审查评测</b><small>查看命中、误报和漏报</small></button>
        </div>
        <div class="workspace-management">
          <button type="button" :disabled="parsing" @click="emit('reparse')">{{ parsing ? '解析中…' : '重新解析源码' }}</button>
          <button type="button" :disabled="rebuilding" @click="emit('rebuild')">{{ rebuilding ? '重建中…' : '重建解析结构' }}</button>
          <button type="button" @click="emit('edit')">编辑项目信息</button>
          <button class="danger" type="button" :disabled="deleting" @click="emit('delete')">{{ deleting ? '删除中…' : '删除项目' }}</button>
        </div>
      </details>
    </template>
  </section>
</template>
