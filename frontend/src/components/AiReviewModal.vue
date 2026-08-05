<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type {
  AiConclusionType,
  AiReview,
  AiReviewFinding,
  FindingSeverity,
  ReviewFeedbackType,
} from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()
const report = ref<AiReview | null>(null)
const loading = ref(false)
const running = ref(false)
const runningMode = ref<'fixed' | 'agent' | null>(null)
const errorMessage = ref('')
const feedbackErrorMessage = ref('')
const feedbackSubmittingId = ref<string>()
const feedbackDrafts = ref<Record<string, string>>({})

const severityLabel: Record<FindingSeverity, string> = {
  INFO: '提示',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重',
}

const conclusionLabel: Record<AiConclusionType, string> = {
  FACT: '事实',
  INFERENCE: '推断',
  NEEDS_VERIFICATION: '待验证',
}

const feedbackLabel: Record<ReviewFeedbackType, string> = {
  ACCEPTED: '采纳',
  REJECTED: '驳回',
  FALSE_POSITIVE: '误报',
  DEFERRED: '稍后处理',
}

const feedbackOptions = Object.entries(feedbackLabel) as [ReviewFeedbackType, string][]

function syncFeedbackDrafts(review: AiReview) {
  feedbackDrafts.value = Object.fromEntries(
    review.findings.map((finding) => [finding.id, finding.feedback?.comment || '']),
  )
}

async function loadLatest() {
  if (!props.projectId) return
  loading.value = true
  report.value = null
  errorMessage.value = ''
  feedbackErrorMessage.value = ''
  try {
    const latest = await projectApi.latestAiReview(props.projectId)
    report.value = latest
    syncFeedbackDrafts(latest)
  } catch (error) {
    if (!(error instanceof ApiError) || error.code !== 40400) {
      errorMessage.value = error instanceof ApiError ? error.message : '读取AI审查记录失败'
    }
  } finally {
    loading.value = false
  }
}

async function runReview(mode: 'fixed' | 'agent') {
  if (!props.projectId) return
  running.value = true
  runningMode.value = mode
  errorMessage.value = ''
  feedbackErrorMessage.value = ''
  try {
    const created = mode === 'agent'
      ? await projectApi.createAgentAiReview(props.projectId)
      : await projectApi.createAiReview(props.projectId)
    report.value = created
    syncFeedbackDrafts(created)
  } catch (error) {
    const message = error instanceof ApiError ? error.message : 'AI审查执行失败'
    await loadLatest()
    errorMessage.value = message
  } finally {
    running.value = false
    runningMode.value = null
  }
}

async function submitFeedback(finding: AiReviewFinding, feedbackType: ReviewFeedbackType) {
  if (!props.projectId) return
  feedbackSubmittingId.value = finding.id
  feedbackErrorMessage.value = ''
  try {
    const feedback = await projectApi.upsertReviewFeedback(props.projectId, finding.id, {
      feedbackType,
      comment: feedbackDrafts.value[finding.id]?.trim() || undefined,
    })
    if (report.value) {
      report.value = {
        ...report.value,
        findings: report.value.findings.map((item) => (
          item.id === finding.id ? { ...item, feedback } : item
        )),
      }
    }
  } catch (error) {
    feedbackErrorMessage.value = error instanceof ApiError ? error.message : '保存审查反馈失败'
  } finally {
    feedbackSubmittingId.value = undefined
  }
}

function formatLatency(value: number) {
  if (value < 1000) return `${value} ms`
  return `${(value / 1000).toFixed(1)} s`
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (open) void loadLatest()
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal ai-review-modal" role="dialog" aria-modal="true" aria-labelledby="ai-review-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">EVIDENCE-GROUNDED REVIEW</p>
          <h2 id="ai-review-title">{{ projectName }} · AI 代码审查</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <div class="ai-review-intro">
        <div>
          <b>调用模型前需要完成当前版本的 Diff、静态分析和知识索引。</b>
          <p>模型只会引用服务端提供的 Chunk，真实文件和行号由 Java 校验。本操作会产生模型额度消耗。</p>
        </div>
        <div class="ai-review-actions">
          <button data-testid="fixed-review" class="button" type="button" :disabled="running || loading" @click="runReview('fixed')">
            {{ runningMode === 'fixed' ? '固定审查中…' : '固定流水线' }}
          </button>
          <button data-testid="agent-review" class="button primary" type="button" :disabled="running || loading" @click="runReview('agent')">
            {{ runningMode === 'agent' ? 'Agent 取证中…' : 'Agent 智能取证' }}
          </button>
        </div>
      </div>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="feedbackErrorMessage" class="notice error" role="alert">
        {{ feedbackErrorMessage }}
      </div>
      <div v-if="loading" class="source-loading">正在读取最近一次 AI 审查记录…</div>
      <div v-else-if="running" class="source-loading">
        {{ runningMode === 'agent' ? 'Agent 正在选择只读工具并收集代码证据，请勿重复提交…' : '正在检索固定变更上下文并调用模型，请勿重复提交…' }}
      </div>
      <div v-else-if="!report" class="state-box compact">
        <h3>还没有 AI 审查记录</h3>
        <p>选择固定流水线或 Agent 智能取证后，后端会固定版本、检索证据、调用模型并校验结构化结论。</p>
      </div>
      <template v-else>
        <div class="ai-review-summary">
          <span><small>模型</small><b>{{ report.provider }} / {{ report.modelName }}</b></span>
          <span><small>证据 Chunk</small><b>{{ report.contextChunks }}</b></span>
          <span><small>有效 / 拒绝</small><b>{{ report.findingCount }} / {{ report.rejectedFindings }}</b></span>
          <span><small>Token / 耗时</small><b>{{ report.totalTokens }} / {{ formatLatency(report.latencyMs) }}</b></span>
        </div>

        <div v-if="report.status === 'FAILED'" class="notice error">
          最近一次审查失败：{{ report.errorMessage || '请检查模型配置后重试' }}
        </div>
        <section v-if="report.toolCalls.length" class="tool-call-panel">
          <div class="tool-call-title">
            <div><b>Agent 工具调用链</b><small>只记录参数结构和结果摘要，不保存完整源码或查询内容</small></div>
            <span>{{ report.toolCalls.length }} 步</span>
          </div>
          <ol class="tool-call-list">
            <li v-for="call in report.toolCalls" :key="call.id" :class="call.status.toLowerCase()">
              <span class="tool-step">{{ call.stepNo }}</span>
              <div>
                <b>{{ call.toolName }}</b>
                <code>{{ call.argumentsSummary }}</code>
                <small>{{ call.resultSummary || call.errorMessage || '等待结果' }}</small>
              </div>
              <em>{{ call.status }} · {{ formatLatency(call.latencyMs) }}</em>
            </li>
          </ol>
        </section>
        <div v-if="report.status !== 'FAILED' && !report.findings.length" class="state-box compact">
          <h3>本次没有通过证据校验的语义风险</h3>
          <p>这不等于代码绝对安全；请结合静态分析、测试、压测和人工审查共同判断。</p>
        </div>
        <div v-else-if="report.status !== 'FAILED'" class="finding-list ai-finding-list">
          <article v-for="finding in report.findings" :key="finding.id" class="finding-card ai-finding-card">
            <div class="finding-head">
              <span class="severity" :class="finding.severity.toLowerCase()">
                {{ severityLabel[finding.severity] }}风险
              </span>
              <span class="conclusion" :class="finding.conclusionType.toLowerCase()">
                {{ conclusionLabel[finding.conclusionType] }}
              </span>
              <b>{{ finding.category }}</b>
              <span>置信度 {{ Math.round(finding.confidence * 100) }}%</span>
            </div>
            <h3>{{ finding.title }}</h3>
            <code>{{ finding.filePath }}:{{ finding.startLine }}–{{ finding.endLine }} · Chunk {{ finding.chunkId }}</code>
            <dl>
              <div><dt>证据</dt><dd>{{ finding.evidence }}</dd></div>
              <div><dt>触发场景</dt><dd>{{ finding.riskScenario }}</dd></div>
              <div><dt>修改方向</dt><dd>{{ finding.suggestion }}</dd></div>
              <div><dt>验证方法</dt><dd>{{ finding.verification }}</dd></div>
            </dl>
            <section class="review-feedback-panel">
              <div class="review-feedback-title">
                <div>
                  <b>人工反馈</b>
                  <small>用于统计误报并改进规则、检索和 Prompt，不会重新调用模型</small>
                </div>
                <span v-if="finding.feedback">
                  当前：{{ feedbackLabel[finding.feedback.feedbackType] }}
                </span>
              </div>
              <textarea
                v-model="feedbackDrafts[finding.id]"
                :data-testid="`feedback-comment-${finding.id}`"
                maxlength="1000"
                rows="2"
                placeholder="可选：说明采纳依据、驳回原因或误报上下文"
              />
              <div class="review-feedback-actions">
                <button
                  v-for="[feedbackType, label] in feedbackOptions"
                  :key="feedbackType"
                  :data-testid="`feedback-${feedbackType}-${finding.id}`"
                  :class="{ active: finding.feedback?.feedbackType === feedbackType }"
                  type="button"
                  :disabled="feedbackSubmittingId === finding.id"
                  @click="submitFeedback(finding, feedbackType)"
                >
                  {{ feedbackSubmittingId === finding.id ? '保存中…' : label }}
                </button>
              </div>
            </section>
          </article>
        </div>
      </template>
    </section>
  </div>
</template>
