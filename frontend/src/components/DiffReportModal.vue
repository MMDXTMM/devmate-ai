<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { CoverageStatus, ReviewDiff } from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()
const report = ref<ReviewDiff | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const coverageLabel: Record<CoverageStatus, string> = {
  FULL: '完整映射',
  PARTIAL: '部分映射',
  SKIPPED: '已跳过',
}

async function createReport() {
  if (!props.projectId) return
  loading.value = true
  report.value = null
  errorMessage.value = ''
  try {
    report.value = await projectApi.createReviewDiff(props.projectId)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'Diff覆盖报告生成失败'
  } finally {
    loading.value = false
  }
}

function shortRevision(revision?: string) {
  return revision?.slice(0, 8) || '—'
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (open) void createReport()
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal diff-modal" role="dialog" aria-modal="true" aria-labelledby="diff-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">DIFF COVERAGE</p>
          <h2 id="diff-title">{{ projectName }} · 变更覆盖</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <div v-if="errorMessage" class="notice error" role="alert">
        {{ errorMessage }}
        <button class="text-button" type="button" @click="createReport">重试</button>
      </div>
      <div v-if="loading" class="source-loading">正在比较最近两个提交并映射源码符号…</div>
      <template v-else-if="report">
        <div class="diff-summary">
          <span><small>版本</small><b>{{ shortRevision(report.baseRevision) }} → {{ shortRevision(report.targetRevision) }}</b></span>
          <span><small>变更文件</small><b>{{ report.changedFiles }}</b></span>
          <span class="full"><small>完整映射</small><b>{{ report.fullyMappedFiles }}</b></span>
          <span class="partial"><small>部分映射</small><b>{{ report.partiallyMappedFiles }}</b></span>
          <span class="skipped"><small>跳过</small><b>{{ report.skippedFiles }}</b></span>
        </div>
        <div class="diff-files">
          <article v-for="file in report.files" :key="file.id">
            <div class="diff-file-head">
              <span class="change-type">{{ file.changeType }}</span>
              <code>{{ file.newPath || file.oldPath }}</code>
              <span class="coverage" :class="file.coverageStatus.toLowerCase()">
                {{ coverageLabel[file.coverageStatus] }}
              </span>
            </div>
            <p class="diff-count"><b>+{{ file.additions }}</b><i>-{{ file.deletions }}</i></p>
            <p v-if="file.mappedSymbols.length" class="mapped-symbols">
              <span
                v-for="symbol in file.mappedSymbols"
                :key="`${symbol.revisionSide}:${symbol.chunkId || symbol.symbolName}:${symbol.startLine}`"
              >
                {{ symbol.revisionSide === 'BASE' ? '基准' : '目标' }} ·
                {{ symbol.symbolName }} · {{ symbol.startLine }}–{{ symbol.endLine }}行
              </span>
            </p>
            <small v-if="file.skipReason" class="skip-reason">{{ file.skipReason }}</small>
          </article>
        </div>
      </template>
    </section>
  </div>
</template>
