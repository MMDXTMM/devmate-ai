<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type {
  BusinessFeature,
  BusinessFeatureDetail,
  BusinessModule,
  ProjectBusinessMap,
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

const emit = defineEmits<{ close: [] }>()

const documents = ref<SourceDocument[]>([])
const businessMap = ref<ProjectBusinessMap | null>(null)
const featureDetail = ref<BusinessFeatureDetail | null>(null)
const symbols = ref<SourceSymbol[]>([])
const references = ref<SourceReference[]>([])
const selectedDocumentId = ref('')
const selectedSymbolId = ref('')
const selectedModuleId = ref('')
const selectedFeatureId = ref('')
const activeView = ref<'BUSINESS' | 'FILES'>('BUSINESS')
const symbolDetail = ref<SourceSymbolDetail | null>(null)
const loadingBusinessMap = ref(false)
const loadingFeatureDetail = ref(false)
const loadingDocuments = ref(false)
const loadingSymbols = ref(false)
const loadingDetail = ref(false)
const copiedSymbolId = ref('')
const errorMessage = ref('')

function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '源码结构加载失败'
}

async function loadBusinessMap() {
  if (!props.projectId) return
  loadingBusinessMap.value = true
  errorMessage.value = ''
  businessMap.value = null
  featureDetail.value = null
  selectedModuleId.value = ''
  selectedFeatureId.value = ''
  try {
    businessMap.value = await projectApi.getBusinessMap(props.projectId)
    const firstModule = businessMap.value.modules[0]
    if (firstModule) {
      await selectModule(firstModule)
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loadingBusinessMap.value = false
  }
}

async function selectModule(module: BusinessModule) {
  selectedModuleId.value = module.id
  const firstFeature = module.features[0]
  if (firstFeature) await selectFeature(firstFeature)
}

async function selectFeature(feature: BusinessFeature) {
  if (!props.projectId) return
  selectedFeatureId.value = feature.id
  featureDetail.value = null
  loadingFeatureDetail.value = true
  errorMessage.value = ''
  try {
    const detail = await projectApi.getBusinessFeatureDetail(props.projectId, feature.id)
    if (selectedFeatureId.value === feature.id) featureDetail.value = detail
  } catch (error) {
    if (selectedFeatureId.value === feature.id) errorMessage.value = errorText(error)
  } finally {
    if (selectedFeatureId.value === feature.id) loadingFeatureDetail.value = false
  }
}

async function switchView(view: 'BUSINESS' | 'FILES') {
  activeView.value = view
  errorMessage.value = ''
  if (view === 'FILES' && documents.value.length === 0) await loadDocuments()
  if (view === 'BUSINESS' && !businessMap.value) await loadBusinessMap()
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
    documents.value = loadedDocuments
    references.value = loadedReferences
    if (documents.value.length > 0) {
      await selectDocument(documents.value[0].id)
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loadingDocuments.value = false
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
  }[document.sourceKind]
}

function documentItemLabel(document: SourceDocument) {
  return document.sourceKind === 'SOURCE_CODE' ? '符号' : '结构项'
}

const symbolTypeLabels: Record<SourceSymbolType, string> = {
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
  selectedDocumentId.value = documentId
  selectedSymbolId.value = ''
  symbolDetail.value = null
  loadingSymbols.value = true
  errorMessage.value = ''
  try {
    symbols.value = await projectApi.listSourceSymbols(props.projectId, documentId)
    if (symbols.value.length > 0) {
      await selectSymbol(symbols.value[0])
    }
  } catch (error) {
    symbols.value = []
    errorMessage.value = errorText(error)
  } finally {
    loadingSymbols.value = false
  }
}

async function selectSymbol(symbol: SourceSymbol) {
  if (!props.projectId) return
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
    if (selectedSymbolId.value === symbol.id) symbolDetail.value = detail
  } catch (error) {
    if (selectedSymbolId.value === symbol.id) errorMessage.value = errorText(error)
  } finally {
    if (selectedSymbolId.value === symbol.id) loadingDetail.value = false
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
    if (open) {
      activeView.value = 'BUSINESS'
      void loadBusinessMap()
    }
  },
  { immediate: true },
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
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <nav class="source-view-tabs" aria-label="项目理解方式">
        <button type="button" :class="{ active: activeView === 'BUSINESS' }" @click="switchView('BUSINESS')">
          业务地图
          <small>先看功能、接口和实现链路</small>
        </button>
        <button type="button" :class="{ active: activeView === 'FILES' }" @click="switchView('FILES')">
          文件结构
          <small>按文件、类和方法查阅源码</small>
        </button>
      </nav>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>

      <div v-if="activeView === 'BUSINESS' && loadingBusinessMap" class="source-loading">
        正在根据接口和调用关系整理业务地图…
      </div>
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
