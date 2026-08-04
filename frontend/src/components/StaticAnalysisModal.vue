<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { FindingSeverity, StaticAnalysis } from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()
const report = ref<StaticAnalysis | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const severityLabel: Record<FindingSeverity, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重',
}

async function runAnalysis() {
  if (!props.projectId) return
  loading.value = true
  report.value = null
  errorMessage.value = ''
  try {
    report.value = await projectApi.createStaticAnalysis(props.projectId)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '静态分析执行失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (open) void runAnalysis()
  },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal analysis-modal" role="dialog" aria-modal="true" aria-labelledby="analysis-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">DETERMINISTIC ANALYSIS</p>
          <h2 id="analysis-title">{{ projectName }} · 静态分析</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <div v-if="errorMessage" class="notice error" role="alert">
        {{ errorMessage }}
        <button class="text-button" type="button" @click="runAnalysis">重试</button>
      </div>
      <div v-if="loading" class="source-loading">正在对本次变更运行 PMD 与项目规则…</div>
      <template v-else-if="report">
        <div class="analysis-summary">
          <span><small>工具</small><b>{{ report.toolName }} {{ report.toolVersion }}</b></span>
          <span><small>分析文件</small><b>{{ report.analyzedFiles }}</b></span>
          <span><small>确定性问题</small><b>{{ report.findingCount }}</b></span>
        </div>

        <div v-if="!report.findings.length" class="state-box compact">
          <h3>当前规则未发现直接命中变更行的问题</h3>
          <p>这不代表代码没有风险；后续 RAG 与 AI 审查会继续分析需要更多上下文的并发、事务和一致性问题。</p>
        </div>
        <div v-else class="finding-list">
          <article v-for="finding in report.findings" :key="finding.id" class="finding-card">
            <div class="finding-head">
              <span class="severity" :class="finding.severity.toLowerCase()">
                {{ severityLabel[finding.severity] }}风险
              </span>
              <b>{{ finding.ruleId }}</b>
              <span>{{ finding.category }}</span>
            </div>
            <code>{{ finding.filePath }}:{{ finding.startLine }}–{{ finding.endLine }}</code>
            <p>{{ finding.message }}</p>
            <small>{{ finding.evidence }}</small>
          </article>
        </div>
      </template>
    </section>
  </div>
</template>
