<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { SourceDocument, SourceReference, SourceSymbol } from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()

const documents = ref<SourceDocument[]>([])
const symbols = ref<SourceSymbol[]>([])
const references = ref<SourceReference[]>([])
const selectedDocumentId = ref('')
const loadingDocuments = ref(false)
const loadingSymbols = ref(false)
const errorMessage = ref('')

function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '源码结构加载失败'
}

async function loadDocuments() {
  if (!props.projectId) return
  loadingDocuments.value = true
  errorMessage.value = ''
  symbols.value = []
  selectedDocumentId.value = ''
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
  }[kind]
}

async function selectDocument(documentId: string) {
  if (!props.projectId) return
  selectedDocumentId.value = documentId
  loadingSymbols.value = true
  errorMessage.value = ''
  try {
    symbols.value = await projectApi.listSourceSymbols(props.projectId, documentId)
  } catch (error) {
    symbols.value = []
    errorMessage.value = errorText(error)
  } finally {
    loadingSymbols.value = false
  }
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (open) void loadDocuments()
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal source-modal" role="dialog" aria-modal="true" aria-labelledby="source-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">PARSED SOURCE</p>
          <h2 id="source-title">{{ projectName }} · 源码结构</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="loadingDocuments" class="source-loading">正在读取源码文件…</div>
      <div v-else-if="documents.length === 0" class="source-empty">
        <b>当前版本还没有结构化源码</b>
        <span>请先重新导入源码，系统会解析类、方法、注解和行号。</span>
      </div>
      <div v-else class="source-browser">
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
              {{ document.sourceKind === 'CONFIGURATION' ? '配置' : '源码' }} ·
              {{ document.chunkCount }} 个{{ document.sourceKind === 'CONFIGURATION' ? '配置项' : '符号' }}
            </small>
          </button>
        </aside>
        <div class="source-symbols">
          <div v-if="loadingSymbols" class="source-loading">正在读取符号…</div>
          <div v-else-if="symbols.length === 0" class="source-empty compact">该文件没有可识别的类型或方法</div>
          <template v-else>
            <article v-for="symbol in symbols" :key="symbol.id">
              <div>
                <span class="symbol-type">{{ symbol.chunkType }}</span>
                <code>{{ symbol.symbolName }}</code>
              </div>
              <small>第 {{ symbol.startLine }}–{{ symbol.endLine }} 行</small>
              <p v-if="symbol.annotations.length">
                <span v-for="annotation in symbol.annotations" :key="annotation">@{{ annotation }}</span>
              </p>
              <ul v-if="referencesFor(symbol.id).length" class="source-references">
                <li v-for="reference in referencesFor(symbol.id)" :key="reference.id">
                  <span class="reference-kind">{{ referenceLabel(reference.referenceKind) }}</span>
                  <code>
                    {{ reference.qualifier ? `${reference.qualifier}.` : '' }}{{ reference.referenceName }}
                  </code>
                  <small>第 {{ reference.startLine }} 行</small>
                  <span v-if="reference.targetSymbolName" class="resolved-reference">
                    → {{ reference.targetSymbolName }}
                    <small v-if="reference.targetFilePath">（{{ reference.targetFilePath }}）</small>
                  </span>
                </li>
              </ul>
            </article>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>
