<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ProjectGuideView from './ProjectGuideView.vue'
import { ApiError, projectApi } from '../services/projectApi'
import type {
  BusinessFeature,
  BusinessFeatureDetail,
  BusinessModule,
  ProjectBusinessMap,
  ProjectUnderstandingReport,
  SourceDocument,
  SourceReference,
  SourceSymbol,
  SourceSymbolDetail,
  SourceSymbolType,
} from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: []; configureModel: []; chat: [question: string] }>()

const documents = ref<SourceDocument[]>([])
const businessMap = ref<ProjectBusinessMap | null>(null)
const understandingReport = ref<ProjectUnderstandingReport | null>(null)
const featureDetail = ref<BusinessFeatureDetail | null>(null)
const symbols = ref<SourceSymbol[]>([])
const references = ref<SourceReference[]>([])
const selectedDocumentId = ref('')
const selectedSymbolId = ref('')
const selectedModuleId = ref('')
const selectedFeatureId = ref('')
const activeView = ref<'GUIDE' | 'BUSINESS' | 'FILES'>('GUIDE')
const symbolDetail = ref<SourceSymbolDetail | null>(null)
const loadingBusinessMap = ref(false)
const generatingUnderstanding = ref(false)
const loadingUnderstanding = ref(false)
const loadingFeatureDetail = ref(false)
const loadingDocuments = ref(false)
const loadingSymbols = ref(false)
const loadingDetail = ref(false)
const copiedSymbolId = ref('')
const errorMessage = ref('')
const summaryExpanded = ref(false)
let reportGeneration = 0
let reportAttemptKey: string | undefined
onBeforeUnmount(() => { reportGeneration += 1 })

const SUMMARY_COLLAPSE_THRESHOLD = 180
const hasLongUnderstandingSummary = computed(
  () => (understandingReport.value?.executiveSummary?.length ?? 0) > SUMMARY_COLLAPSE_THRESHOLD,
)
const displayedUnderstandingSummary = computed(() => {
  const summary = understandingReport.value?.executiveSummary ?? ''
  if (!hasLongUnderstandingSummary.value || summaryExpanded.value) return summary
  return `${summary.slice(0, SUMMARY_COLLAPSE_THRESHOLD).trimEnd()}…`
})
const understandingSummaryId = computed(
  () => `understanding-summary-${understandingReport.value?.id ?? 'latest'}`,
)

function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '源码结构加载失败'
}

async function loadBusinessMap() {
  if (!props.projectId) return
  const generation = reportGeneration
  const projectId = props.projectId
  loadingBusinessMap.value = true
  errorMessage.value = ''
  businessMap.value = null
  featureDetail.value = null
  selectedModuleId.value = ''
  selectedFeatureId.value = ''
  try {
    const result = await projectApi.getBusinessMap(projectId)
    if (generation !== reportGeneration) return
    businessMap.value = result
    void loadLatestUnderstandingReport(true)
    const firstModule = businessMap.value.modules[0]
    if (firstModule) {
      await selectModule(firstModule)
    }
  } catch (error) {
    if (generation === reportGeneration) errorMessage.value = errorText(error)
  } finally {
    if (generation === reportGeneration) loadingBusinessMap.value = false
  }
}

async function generateUnderstandingReport() {
  if (!props.projectId || !businessMap.value) return
  const generation = reportGeneration
  const projectId = props.projectId
  const revision = businessMap.value.revision
  reportAttemptKey ??= crypto.randomUUID()
  generatingUnderstanding.value = true
  errorMessage.value = ''
  try {
    const report = await projectApi.createUnderstandingReport(
      projectId,
      revision,
      reportAttemptKey,
    )
    if (generation === reportGeneration && report.projectId === projectId && report.revision === revision) {
      understandingReport.value = report
      reportAttemptKey = undefined
    }
  } catch (error) {
    if (generation === reportGeneration) {
      errorMessage.value = error instanceof ApiError ? error.message : '项目导览生成失败'
      if (error instanceof ApiError && error.status) reportAttemptKey = undefined
    }
  } finally {
    if (generation === reportGeneration) generatingUnderstanding.value = false
  }
}

async function loadLatestUnderstandingReport(silent = false) {
  if (!props.projectId) return
  const generation = reportGeneration
  const projectId = props.projectId
  loadingUnderstanding.value = true
  errorMessage.value = ''
  try {
    const report = await projectApi.latestUnderstandingReport(projectId)
    if (generation !== reportGeneration) return
    if (report.projectId !== projectId || report.revision !== businessMap.value?.revision) {
      understandingReport.value = null
      reportAttemptKey = undefined
      errorMessage.value = '上次报告对应其他源码版本，请为当前版本生成新的报告'
      return
    }
    understandingReport.value = report
  } catch (error) {
    if (generation !== reportGeneration) return
    understandingReport.value = null
    reportAttemptKey = undefined
    if (!(silent && error instanceof ApiError && error.status === 404)) {
      errorMessage.value = error instanceof ApiError && error.status === 404
        ? '当前项目还没有AI深度理解报告'
        : errorText(error)
    }
  } finally {
    if (generation === reportGeneration) loadingUnderstanding.value = false
  }
}

async function selectModule(module: BusinessModule) {
  selectedModuleId.value = module.id
  const firstFeature = module.features[0]
  if (firstFeature) await selectFeature(firstFeature)
}

async function selectFeature(feature: BusinessFeature) {
  if (!props.projectId) return
  const generation = reportGeneration
  selectedFeatureId.value = feature.id
  featureDetail.value = null
  loadingFeatureDetail.value = true
  errorMessage.value = ''
  try {
    const detail = await projectApi.getBusinessFeatureDetail(props.projectId, feature.id)
    if (generation === reportGeneration && selectedFeatureId.value === feature.id) featureDetail.value = detail
  } catch (error) {
    if (generation === reportGeneration && selectedFeatureId.value === feature.id) errorMessage.value = errorText(error)
  } finally {
    if (generation === reportGeneration && selectedFeatureId.value === feature.id) loadingFeatureDetail.value = false
  }
}

async function switchView(view: 'GUIDE' | 'BUSINESS' | 'FILES') {
  activeView.value = view
  errorMessage.value = ''
  if (view === 'FILES' && documents.value.length === 0) await loadDocuments()
  if ((view === 'GUIDE' || view === 'BUSINESS') && !businessMap.value) await loadBusinessMap()
}

async function openJourney(moduleId: string) {
  const module = businessMap.value?.modules.find((item) => item.id === moduleId)
  if (!module) return
  activeView.value = 'BUSINESS'
  await selectModule(module)
}

function selectedModule() {
  return businessMap.value?.modules.find((module) => module.id === selectedModuleId.value)
}

function evidenceLayerLabel(layer: BusinessFeatureDetail['implementation'][number]['layer']) {
  return {
    CONTROLLER: '接口入口',
    SERVICE: '业务服务',
    DATA_ACCESS: '数据访问',
    SUPPORTING_CODE: '关联实现',
  }[layer]
}

async function loadDocuments() {
  if (!props.projectId) return
  const generation = reportGeneration
  loadingDocuments.value = true
  errorMessage.value = ''
  symbols.value = []
  selectedDocumentId.value = ''
  selectedSymbolId.value = ''
  symbolDetail.value = null
  try {
    const [loadedDocuments, loadedReferences] = await Promise.all([
      projectApi.listSourceDocuments(props.projectId),
      projectApi.listSourceReferences(props.projectId),
    ])
    if (generation !== reportGeneration) return
    documents.value = loadedDocuments
    references.value = loadedReferences
    if (documents.value.length > 0) {
      await selectDocument(documents.value[0].id)
    }
  } catch (error) {
    if (generation === reportGeneration) errorMessage.value = errorText(error)
  } finally {
    if (generation === reportGeneration) loadingDocuments.value = false
  }
}

function referencesFor(symbolId: string) {
  return references.value.filter((reference) => reference.sourceChunkId === symbolId)
}

function referenceLabel(kind: SourceReference['referenceKind']) {
  return {
    METHOD_CALL: '方法调用',
    DATA_ACCESS: '数据访问',
    CONFIG_KEY: '配置键',
    CONFIG_PREFIX: '配置前缀',
    DATABASE_TABLE: '数据库表',
  }[kind]
}

function documentKindLabel(document: SourceDocument) {
  return {
    SOURCE_CODE: '源码',
    CONFIGURATION: '配置',
    DATABASE_SCHEMA: '数据库结构',
    DOCUMENTATION: '项目文档',
  }[document.sourceKind]
}

function documentItemLabel(document: SourceDocument) {
  return document.sourceKind === 'SOURCE_CODE' ? '符号' : '结构项'
}

const symbolTypeLabels: Record<SourceSymbolType, string> = {
  TEXT_EXCERPT: '文本片段（非语法解析）',
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

function symbolTypeLabel(type: SourceSymbolType) {
  return symbolTypeLabels[type]
}

function symbolExplanation(symbol: SourceSymbolDetail) {
  const location = `位于第 ${symbol.startLine}-${symbol.endLine} 行。`
  const explanations: Record<SourceSymbolType, string> = {
    TEXT_EXCERPT: '这是保留原始行号的通用文本片段，未进行语言语法与调用关系解析。',
    FILE_HEADER: '这里包含包声明和依赖导入，可以先判断代码所属模块以及依赖了哪些外部能力。',
    IMPORT: '这是当前文件使用的外部类型或工具，能帮助判断代码依赖方向。',
    CLASS: '这是类或接口的整体定义，建议先看类注解、实现关系和公开方法，再进入具体方法。',
    CONSTRUCTOR: '这是对象创建入口，参数通常反映该组件运行时必须依赖的能力。',
    METHOD: '这是可执行的方法实现，重点关注输入参数、状态变化、外部调用和返回值。',
    CONFIG_PROPERTY: '这是运行配置项，代码行为会受到该配置值影响。',
    DATABASE_TABLE: '这是数据库表定义，用来确认业务数据的持久化边界。',
    DATABASE_COLUMN: '这是数据库字段定义，可用于理解数据类型、是否必填和字段职责。',
    DATABASE_INDEX: '这是数据库索引定义，用来支撑对应条件下的查询效率。',
    DATABASE_CONSTRAINT: '这是数据库约束，用来保证数据关系或取值合法。',
    DATABASE_CHANGE: '这是数据库迁移中的结构变更，需要结合迁移版本理解表结构演进。',
  }
  return `${location}${explanations[symbol.chunkType]}`
}

async function selectDocument(documentId: string) {
  if (!props.projectId) return
  const generation = reportGeneration
  selectedDocumentId.value = documentId
  selectedSymbolId.value = ''
  symbolDetail.value = null
  loadingSymbols.value = true
  errorMessage.value = ''
  try {
    const result = await projectApi.listSourceSymbols(props.projectId, documentId)
    if (generation !== reportGeneration || selectedDocumentId.value !== documentId) return
    symbols.value = result
    if (symbols.value.length > 0) {
      await selectSymbol(symbols.value[0])
    }
  } catch (error) {
    if (generation !== reportGeneration || selectedDocumentId.value !== documentId) return
    symbols.value = []
    errorMessage.value = errorText(error)
  } finally {
    if (generation === reportGeneration && selectedDocumentId.value === documentId) loadingSymbols.value = false
  }
}

async function selectSymbol(symbol: SourceSymbol) {
  if (!props.projectId) return
  const generation = reportGeneration
  selectedSymbolId.value = symbol.id
  symbolDetail.value = null
  loadingDetail.value = true
  errorMessage.value = ''
  try {
    const detail = await projectApi.getSourceSymbolDetail(
      props.projectId,
      symbol.documentId,
      symbol.id,
    )
    if (generation === reportGeneration && selectedSymbolId.value === symbol.id) symbolDetail.value = detail
  } catch (error) {
    if (generation === reportGeneration && selectedSymbolId.value === symbol.id) errorMessage.value = errorText(error)
  } finally {
    if (generation === reportGeneration && selectedSymbolId.value === symbol.id) loadingDetail.value = false
  }
}

async function copyCode() {
  if (!symbolDetail.value) return
  try {
    await navigator.clipboard.writeText(symbolDetail.value.code)
    copiedSymbolId.value = symbolDetail.value.id
    window.setTimeout(() => {
      if (copiedSymbolId.value === symbolDetail.value?.id) copiedSymbolId.value = ''
    }, 1500)
  } catch {
    errorMessage.value = '复制失败，请手动选择代码'
  }
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    reportGeneration += 1
    understandingReport.value = null
    reportAttemptKey = undefined
    generatingUnderstanding.value = false
    loadingUnderstanding.value = false
    loadingBusinessMap.value = false
    loadingDocuments.value = false
    loadingSymbols.value = false
    loadingDetail.value = false
    loadingFeatureDetail.value = false
    businessMap.value = null
    featureDetail.value = null
    documents.value = []
    symbols.value = []
    references.value = []
    symbolDetail.value = null
    selectedDocumentId.value = ''
    selectedSymbolId.value = ''
    selectedModuleId.value = ''
    selectedFeatureId.value = ''
    errorMessage.value = ''
    if (open) {
      activeView.value = 'GUIDE'
      void loadBusinessMap()
    }
  },
  { immediate: true },
)

watch(
  () => understandingReport.value?.id,
  () => {
    summaryExpanded.value = false
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal source-modal" role="dialog" aria-modal="true" aria-labelledby="source-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">项目深层理解</p>
          <h2 id="source-title">{{ projectName }} · 业务与源码</h2>
        </div>
        <div class="source-chat-actions"><button class="button secondary" type="button" @click="emit('chat', '')">与 AI 聊这个项目</button></div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <nav class="source-view-tabs" aria-label="项目理解方式">
        <button type="button" :class="{ active: activeView === 'GUIDE' }" @click="switchView('GUIDE')">
          新人导览
          <small>先理解项目目标和核心流程</small>
        </button>
        <button type="button" :class="{ active: activeView === 'BUSINESS' }" @click="switchView('BUSINESS')">
          Java 实现与接口
          <small>下钻功能、接口和实现链路</small>
        </button>
        <button type="button" :class="{ active: activeView === 'FILES' }" @click="switchView('FILES')">
          文件结构（高级）
          <small>按文件、类和方法查阅源码</small>
        </button>
      </nav>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>

      <div v-if="(activeView === 'GUIDE' || activeView === 'BUSINESS') && loadingBusinessMap" class="source-loading">
        正在读取项目导览…
      </div>
      <main v-else-if="activeView === 'GUIDE' && businessMap" class="onboarding-view">
        <section class="ai-understanding-panel guide-controls">
          <header><div><span class="eyebrow">先看懂，再深入</span><h3>项目通俗导览</h3><p>先了解用途、能力和主要流程，再选择一件事查看规则与实现依据。</p></div>
            <div class="ai-understanding-actions">
              <button class="button secondary" type="button" :disabled="loadingUnderstanding || generatingUnderstanding" @click="loadLatestUnderstandingReport()">{{ loadingUnderstanding ? '读取中…' : '查看上次导览' }}</button>
              <button class="button primary" type="button" :disabled="generatingUnderstanding || loadingUnderstanding" @click="generateUnderstandingReport">{{ generatingUnderstanding ? '正在阅读与归纳…' : '生成通俗导览' }}</button>
            </div>
          </header>
          <p v-if="!understandingReport?.guide">尚无当前版本的通俗导览。生成会使用当前账户已启用的模型并消耗 API 额度；不会自动调用。</p>
          <div v-if="understandingReport?.status === 'FAILED'" class="notice error">{{ understandingReport.errorMessage || '生成失败，请检查模型后重试' }}</div>
          <p v-if="understandingReport?.status === 'RUNNING'">上次生成仍在进行，请稍后查看上次导览。</p>
          <button v-if="!understandingReport?.guide" class="button secondary" type="button" @click="emit('configureModel')">配置或检查大模型连接</button>
        </section>
        <ProjectGuideView v-if="understandingReport?.guide" :guide="understandingReport.guide" @chat="emit('chat', $event)" />
        <details v-if="!understandingReport?.guide" class="legacy-project-understanding">
          <summary>旧版报告与 Java 静态分析（高级）</summary>
          <section class="onboarding-hero">
            <div>
              <span class="eyebrow">先用一句话理解项目</span>
              <h3>{{ businessMap.onboarding.purpose }}</h3>
              <p>{{ businessMap.onboarding.architectureSummary }}</p>
            </div>
            <dl>
              <div><dt>业务模块</dt><dd>{{ businessMap.moduleCount }}</dd></div>
              <div><dt>HTTP 接口</dt><dd>{{ businessMap.endpointCount }}</dd></div>
              <div><dt>状态模型</dt><dd>{{ businessMap.onboarding.stateModels.length }}</dd></div>
              <div><dt>数据表</dt><dd>{{ businessMap.onboarding.dataAssets.length }}</dd></div>
            </dl>
          </section>

          <section class="ai-understanding-panel">
            <header>
              <div>
                <span class="eyebrow">SPRING AI + RAG EVIDENCE</span>
                <h3>AI 深度理解报告</h3>
                <p>让当前账户选择的大模型结合业务地图和真实代码证据，解释核心业务，而不是逐行翻译代码。</p>
              </div>
              <div class="ai-understanding-actions">
                <button class="button secondary" type="button" :disabled="loadingUnderstanding || generatingUnderstanding" @click="loadLatestUnderstandingReport()">
                  {{ loadingUnderstanding ? '读取中…' : '查看上次报告' }}
                </button>
                <button class="button primary" type="button" :disabled="generatingUnderstanding || loadingUnderstanding" @click="generateUnderstandingReport">
                  {{ generatingUnderstanding ? 'AI分析中…' : '生成新的深度报告' }}
                </button>
              </div>
            </header>
            <div v-if="!understandingReport" class="ai-understanding-empty">
              生成报告会调用你在“大模型连接”中启用的模型，并消耗对应 API 额度；页面不会自动调用。
              <button class="button secondary" type="button" @click="emit('configureModel')">配置或检查大模型连接</button>
            </div>
            <div v-else-if="understandingReport.status === 'FAILED'" class="notice error">
              {{ understandingReport.errorMessage || '报告生成失败，请检查模型连接后重试' }}
            </div>
            <div
              v-else-if="understandingReport.status === 'SUCCEEDED'"
              :key="understandingReport.id"
              class="ai-understanding-report"
            >
              <div class="ai-report-heading">
                <div>
                  <span class="eyebrow">报告概览</span>
                  <h4>先看结论，再按需展开实现证据</h4>
                </div>
                <span class="ai-report-model">{{ understandingReport.provider }} / {{ understandingReport.modelName }}</span>
              </div>

              <section class="ai-report-overview">
                <p
                  :id="understandingSummaryId"
                  class="ai-report-summary-text"
                  :class="{ collapsed: hasLongUnderstandingSummary && !summaryExpanded }"
                >{{ displayedUnderstandingSummary }}</p>
                <button
                  v-if="hasLongUnderstandingSummary"
                  class="ai-report-summary-toggle"
                  type="button"
                  :aria-expanded="summaryExpanded"
                  :aria-controls="understandingSummaryId"
                  @click="summaryExpanded = !summaryExpanded"
                >{{ summaryExpanded ? '收起摘要' : '展开完整摘要' }}</button>
                <dl class="ai-report-facts">
                  <div><dt>核心流程</dt><dd>{{ understandingReport.businessFlows.length }}</dd></div>
                  <div><dt>阅读步骤</dt><dd>{{ understandingReport.readingGuide.length }}</dd></div>
                  <div><dt>待确认项</dt><dd>{{ understandingReport.risksAndUnknowns.length }}</dd></div>
                </dl>
              </section>

              <details v-if="understandingReport.architectureNarrative" class="ai-architecture-details">
                <summary>
                  <span>架构与实现说明</span>
                  <small>按需展开</small>
                </summary>
                <p>{{ understandingReport.architectureNarrative }}</p>
              </details>

              <div class="ai-report-section-heading">
                <div><h5>核心业务流程</h5><p>默认收起技术细节，选择一个流程查看步骤和代码证据。</p></div>
                <span>{{ understandingReport.businessFlows.length }} 条</span>
              </div>
              <details v-for="(flow, flowIndex) in understandingReport.businessFlows" :key="flow.name" class="ai-flow-card">
                <summary>
                  <span class="ai-flow-index">{{ String(flowIndex + 1).padStart(2, '0') }}</span>
                  <span class="ai-flow-summary-copy"><b>{{ flow.name }}</b><small>{{ flow.goal }}</small></span>
                  <span class="ai-flow-open-label">查看实现</span>
                </summary>
                <div class="ai-flow-body">
                  <button class="button secondary ask-business-flow" type="button" @click="emit('chat', `请用通俗的话讲解“${flow.name}”：谁会使用、具体步骤、接口入口、数据变化和失败情况。请引用代码证据。`)">让 AI 讲解这条业务</button>
                  <ol><li v-for="step in flow.steps" :key="step">{{ step }}</li></ol>
                  <div class="ai-flow-tags">
                    <code v-for="api in flow.apiEntries" :key="api">{{ api }}</code>
                    <code v-for="change in flow.dataChanges" :key="change">{{ change }}</code>
                  </div>
                  <details v-for="evidence in flow.evidence" :key="evidence.chunkId" class="ai-evidence-details">
                    <summary>{{ evidence.symbolName }} · {{ evidence.filePath }}:{{ evidence.startLine }}</summary>
                    <pre><code>{{ evidence.code }}</code></pre>
                    <small v-if="evidence.truncated">代码较长，当前仅展示受控预览。</small>
                  </details>
                </div>
              </details>
              <section v-if="understandingReport.readingGuide.length" class="ai-reading-guide">
                <h5>推荐阅读顺序</h5>
                <ol><li v-for="item in understandingReport.readingGuide" :key="`${item.order}-${item.title}`">
                  <b>{{ item.order }}. {{ item.title }}</b><span>{{ item.reason }}</span>
                </li></ol>
              </section>
              <details v-if="understandingReport.risksAndUnknowns.length" class="onboarding-unknowns">
                <summary>模型明确标记的未知项</summary>
                <ul><li v-for="item in understandingReport.risksAndUnknowns" :key="item">{{ item }}</li></ul>
              </details>
              <details class="ai-report-diagnostics">
                <summary>生成信息</summary>
                <div class="ai-report-meta">
                  <span>{{ understandingReport.totalTokens }} Tokens</span>
                  <span>{{ understandingReport.promptVersion }}</span>
                </div>
              </details>
            </div>
          </section>

          <section v-if="businessMap.onboarding.detectedCapabilities.length" class="onboarding-section">
            <header><span>01</span><h3>系统具备什么能力</h3></header>
            <div class="capability-list">
              <span v-for="capability in businessMap.onboarding.detectedCapabilities" :key="capability">
                {{ capability }}
              </span>
            </div>
          </section>

          <section class="onboarding-section">
            <header><span>02</span><h3>用户能完成哪些事情</h3><p>先看业务目的，再沿真实接口和调用关系查看实现。</p></header>
            <div class="journey-grid">
              <article v-for="journey in businessMap.onboarding.coreJourneys" :key="journey.moduleId">
                <div class="journey-heading">
                  <div><span>业务功能</span><h4>{{ journey.name }}</h4></div>
                  <button class="button secondary" type="button" @click="openJourney(journey.moduleId)">查看实现代码</button>
                </div>
                <p>{{ journey.goal }}</p>
                <details>
                  <summary>对外接口</summary>
                  <code v-for="api in journey.apiEntries" :key="api">{{ api }}</code>
                </details>
                <details v-if="journey.implementationFlow.length">
                  <summary>代表性实现流程</summary>
                  <ol>
                    <li v-for="step in journey.implementationFlow" :key="step">{{ step }}</li>
                  </ol>
                </details>
                <details v-if="journey.dataOperations.length">
                  <summary>数据变化</summary>
                  <code v-for="operation in journey.dataOperations" :key="operation">{{ operation }}</code>
                </details>
                <div class="journey-warning">
                  <b>失败与边界</b>
                  <span v-for="signal in journey.failureSignals" :key="signal">{{ signal }}</span>
                </div>
              </article>
            </div>
          </section>

          <section class="onboarding-section onboarding-facts">
            <header><span>03</span><h3>状态、数据与阅读顺序</h3></header>
            <div class="onboarding-fact-grid">
              <article>
                <h4>业务状态</h4>
                <div v-if="businessMap.onboarding.stateModels.length" class="fact-list">
                  <div v-for="state in businessMap.onboarding.stateModels" :key="state.chunkId">
                    <b>{{ state.name }}</b>
                    <span>{{ state.values.length ? state.values.join('、') : '已识别状态类型，枚举值待确认' }}</span>
                    <small>仅识别到状态取值；允许的转换顺序需要结合实现确认。</small>
                    <small>{{ state.filePath }} · 第 {{ state.startLine }} 行</small>
                  </div>
                </div>
                <p v-else>暂未识别到以 Status、State 或 Stage 命名的状态模型。</p>
              </article>
              <article>
                <h4>核心数据表</h4>
                <div v-if="businessMap.onboarding.dataAssets.length" class="data-asset-list">
                  <code v-for="asset in businessMap.onboarding.dataAssets" :key="asset.chunkId">{{ asset.name }}</code>
                </div>
                <p v-else>当前版本没有解析到数据库建表迁移。</p>
              </article>
              <article class="reading-order-card">
                <h4>推荐阅读顺序</h4>
                <ol>
                  <li v-for="step in businessMap.onboarding.readingOrder" :key="`${step.order}-${step.filePath}`">
                    <span>{{ String(step.order).padStart(2, '0') }}</span>
                    <div>
                      <b>{{ step.category }} · {{ step.title }}</b>
                      <p>{{ step.reason }}</p>
                      <small>{{ step.filePath }} · 第 {{ step.startLine }} 行</small>
                    </div>
                  </li>
                </ol>
              </article>
            </div>
          </section>

          <details class="onboarding-unknowns">
            <summary>哪些结论仍需要人工确认</summary>
            <ul><li v-for="unknown in businessMap.onboarding.unknowns" :key="unknown">{{ unknown }}</li></ul>
          </details>
        </details>
      </main>
      <div v-else-if="activeView === 'BUSINESS' && businessMap?.modules.length === 0" class="source-empty">
        <b>没有识别到 Spring Web 业务入口</b>
        <span>该项目可能不是 Web 项目，或接口使用了当前版本暂不支持的动态注册方式。</span>
        <button class="button secondary" type="button" @click="switchView('FILES')">继续查看文件结构</button>
      </div>
      <div v-else-if="activeView === 'BUSINESS' && businessMap" class="business-browser">
        <aside class="business-modules" aria-label="业务模块">
          <header>
            <span>项目总体业务</span>
            <b>{{ businessMap.moduleCount }} 个模块 · {{ businessMap.endpointCount }} 个接口</b>
            <p>{{ businessMap.summary }}</p>
          </header>
          <button
            v-for="module in businessMap.modules"
            :key="module.id"
            type="button"
            :class="{ active: selectedModuleId === module.id }"
            @click="selectModule(module)"
          >
            <b>{{ module.name }}</b>
            <span>{{ module.features.length }} 个功能入口</span>
            <small>{{ module.controllerFilePath }}</small>
          </button>
          <details class="business-limitations">
            <summary>当前分析边界</summary>
            <ul>
              <li v-for="limitation in businessMap.limitations" :key="limitation">{{ limitation }}</li>
            </ul>
          </details>
        </aside>

        <div class="business-features" aria-label="业务功能与接口">
          <header v-if="selectedModule()">
            <span>当前业务模块</span>
            <b>{{ selectedModule()?.name }}</b>
            <p>{{ selectedModule()?.description }}</p>
          </header>
          <button
            v-for="feature in selectedModule()?.features || []"
            :key="feature.id"
            type="button"
            :class="{ active: selectedFeatureId === feature.id }"
            @click="selectFeature(feature)"
          >
            <b>{{ feature.name }}</b>
            <code>{{ feature.httpMethods.join('/') }} {{ feature.path }}</code>
            <small>
              {{ feature.implementationSteps }} 层实现证据
              <template v-if="feature.accessesData"> · 包含数据访问</template>
            </small>
          </button>
        </div>

        <section class="business-detail" aria-label="业务实现代码">
          <div v-if="loadingFeatureDetail" class="source-loading">正在展开接口实现链路…</div>
          <div v-else-if="!featureDetail" class="source-empty compact">选择一个业务功能查看接口与实现代码</div>
          <template v-else>
            <header class="business-detail-header">
              <div>
                <span class="eyebrow">功能实现链路</span>
                <h3>{{ featureDetail.feature.name }}</h3>
                <code>{{ featureDetail.feature.httpMethods.join('/') }} {{ featureDetail.feature.path }}</code>
              </div>
            </header>
            <section class="business-flow-summary">
              <b>总体调用逻辑</b>
              <p>{{ featureDetail.flowSummary }}</p>
            </section>
            <section v-if="featureDetail.dataOperations.length" class="business-data-operations">
              <b>涉及的数据操作</b>
              <ul>
                <li v-for="operation in featureDetail.dataOperations" :key="operation"><code>{{ operation }}</code></li>
              </ul>
            </section>
            <div class="business-evidence-list">
              <article v-for="(evidence, index) in featureDetail.implementation" :key="evidence.chunkId">
                <header>
                  <span class="business-step">{{ String(index + 1).padStart(2, '0') }}</span>
                  <span class="symbol-type">{{ evidenceLayerLabel(evidence.layer) }}</span>
                  <code>{{ evidence.symbolName }}</code>
                </header>
                <p>{{ evidence.explanation }}</p>
                <small>{{ evidence.filePath }} · 第 {{ evidence.startLine }}-{{ evidence.endLine }} 行</small>
                <pre><code>{{ evidence.code }}</code></pre>
                <p v-if="evidence.truncated" class="notice warning source-truncated">
                  代码较长，当前展示前 6,000 个字符；原始代码共 {{ evidence.originalCharacters }} 个字符。
                </p>
              </article>
            </div>
          </template>
        </section>
      </div>

      <div v-else-if="activeView === 'FILES' && loadingDocuments" class="source-loading">正在读取源码文件…</div>
      <div v-else-if="activeView === 'FILES' && documents.length === 0" class="source-empty">
        <b>当前版本还没有结构化源码</b>
        <span>请先重新导入源码，系统会解析类、方法、注解和行号。</span>
      </div>
      <div v-else-if="activeView === 'FILES'" class="source-browser">
        <aside class="source-files" aria-label="源码文件">
          <button
            v-for="document in documents"
            :key="document.id"
            type="button"
            :class="{ active: selectedDocumentId === document.id }"
            @click="selectDocument(document.id)"
          >
            <b>{{ document.fileName }}</b>
            <span>{{ document.filePath }}</span>
            <small>
              {{ documentKindLabel(document) }} ·
              {{ document.chunkCount }} 个{{ documentItemLabel(document) }} ·
              {{ document.structureVersion }}
            </small>
          </button>
        </aside>
        <div class="source-symbols" aria-label="源码结构项">
          <div v-if="loadingSymbols" class="source-loading">正在读取符号…</div>
          <div v-else-if="symbols.length === 0" class="source-empty compact">该文件没有可识别的结构项</div>
          <div v-else class="source-symbol-list">
            <button
              v-for="symbol in symbols"
              :key="symbol.id"
              type="button"
              :class="{ active: selectedSymbolId === symbol.id }"
              @click="selectSymbol(symbol)"
            >
              <span class="symbol-type">{{ symbolTypeLabel(symbol.chunkType) }}</span>
              <span class="symbol-list-name">
                <code>{{ symbol.symbolName }}</code>
                <small>第 {{ symbol.startLine }}-{{ symbol.endLine }} 行</small>
              </span>
            </button>
          </div>
        </div>
        <section class="source-code-view" aria-label="源码代码块">
          <div v-if="loadingDetail" class="source-loading">正在读取代码块…</div>
          <div v-else-if="!symbolDetail" class="source-empty compact">选择一个结构项查看真实代码</div>
          <template v-else>
            <header class="source-code-header">
              <div>
                <span class="symbol-type">{{ symbolTypeLabel(symbolDetail.chunkType) }}</span>
                <code>{{ symbolDetail.symbolName }}</code>
              </div>
              <button class="button secondary copy-code-button" type="button" @click="copyCode">
                {{ copiedSymbolId === symbolDetail.id ? '已复制' : '复制代码' }}
              </button>
            </header>
            <p class="source-explanation"><b>代码解读：</b>{{ symbolExplanation(symbolDetail) }}</p>
            <p v-if="symbolDetail.annotations.length" class="source-annotations">
              <span v-for="annotation in symbolDetail.annotations" :key="annotation">@{{ annotation }}</span>
            </p>
            <pre><code>{{ symbolDetail.code }}</code></pre>
            <p v-if="symbolDetail.truncated" class="notice warning source-truncated">
              代码较长，当前展示前 16,000 个字符；原始代码共 {{ symbolDetail.originalCharacters }} 个字符。
            </p>
            <ul v-if="referencesFor(symbolDetail.id).length" class="source-references">
              <li v-for="reference in referencesFor(symbolDetail.id)" :key="reference.id">
                <span class="reference-kind">{{ referenceLabel(reference.referenceKind) }}</span>
                <code>{{ reference.qualifier ? `${reference.qualifier}.` : '' }}{{ reference.referenceName }}</code>
                <small>第 {{ reference.startLine }} 行</small>
                <span v-if="reference.targetSymbolName" class="resolved-reference">
                  调用到 {{ reference.targetSymbolName }}
                  <small v-if="reference.targetFilePath">（{{ reference.targetFilePath }}）</small>
                </span>
              </li>
            </ul>
          </template>
        </section>
      </div>
    </section>
  </div>
</template>
