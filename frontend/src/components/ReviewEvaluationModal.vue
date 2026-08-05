<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type {
  AiFindingCategory,
  AiReview,
  ReviewDiff,
  ReviewEvaluationCase,
  ReviewEvaluationCaseForm,
  ReviewEvaluationRun,
  ReviewExpectationType,
} from '../types/project'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()

const emit = defineEmits<{ close: [] }>()
const datasetVersion = ref('known-defects-v1')
const reviewDiff = ref<ReviewDiff | null>(null)
const latestAiReview = ref<AiReview | null>(null)
const cases = ref<ReviewEvaluationCase[]>([])
const runs = ref<ReviewEvaluationRun[]>([])
const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  caseKey: '',
  name: '',
  expectationType: 'DEFECT' as ReviewExpectationType,
  category: 'CONCURRENCY' as AiFindingCategory,
  filePath: '',
  startLine: 1,
  endLine: 1,
  rationale: '',
})

const categoryOptions: AiFindingCategory[] = [
  'CONCURRENCY', 'TRANSACTION', 'CACHE', 'MESSAGE', 'SQL',
  'SECURITY', 'ARCHITECTURE', 'PERFORMANCE', 'RELIABILITY',
]

const latestFixedRun = computed(() => runs.value.find((run) => run.executionMode === 'FIXED'))
const latestAgentRun = computed(() => runs.value.find((run) => run.executionMode === 'AGENT'))
const canRunEvaluation = computed(() => (
  latestAiReview.value?.status === 'SUCCEEDED'
  && latestAiReview.value.reviewTaskId === reviewDiff.value?.id
  && cases.value.length > 0
))

function readableError(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

async function loadWorkspace() {
  if (!props.projectId) return
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  cases.value = []
  runs.value = []
  reviewDiff.value = null
  latestAiReview.value = null
  try {
    reviewDiff.value = await projectApi.latestReviewDiff(props.projectId)
    try {
      latestAiReview.value = await projectApi.latestAiReview(props.projectId)
    } catch (error) {
      if (!(error instanceof ApiError) || error.code !== 40400) throw error
    }
    await loadDataset()
  } catch (error) {
    errorMessage.value = readableError(error, '读取评测工作台失败')
  } finally {
    loading.value = false
  }
}

async function loadDataset() {
  if (!props.projectId || !reviewDiff.value) return
  const version = datasetVersion.value.trim()
  if (!version) {
    errorMessage.value = '请输入评测集版本'
    return
  }
  errorMessage.value = ''
  try {
    const [loadedCases, loadedRuns] = await Promise.all([
      projectApi.listReviewEvaluationCases(props.projectId, version, reviewDiff.value.id),
      projectApi.listReviewEvaluationRuns(props.projectId, version, reviewDiff.value.id),
    ])
    cases.value = loadedCases
    runs.value = loadedRuns
  } catch (error) {
    errorMessage.value = readableError(error, '读取评测集失败')
  }
}

function resetCaseForm() {
  form.caseKey = ''
  form.name = ''
  form.expectationType = 'DEFECT'
  form.category = 'CONCURRENCY'
  form.filePath = ''
  form.startLine = 1
  form.endLine = 1
  form.rationale = ''
}

async function createCase() {
  if (!props.projectId || !reviewDiff.value) return
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  const isDefect = form.expectationType === 'DEFECT'
  const request: ReviewEvaluationCaseForm = {
    reviewTaskId: reviewDiff.value.id,
    datasetVersion: datasetVersion.value.trim(),
    caseKey: form.caseKey.trim(),
    name: form.name.trim(),
    expectationType: form.expectationType,
    category: isDefect ? form.category : undefined,
    filePath: isDefect ? form.filePath.trim() : undefined,
    startLine: isDefect ? form.startLine : undefined,
    endLine: isDefect ? form.endLine : undefined,
    rationale: form.rationale.trim(),
  }
  try {
    await projectApi.createReviewEvaluationCase(props.projectId, request)
    successMessage.value = '标准答案已加入当前评测集'
    resetCaseForm()
    await loadDataset()
  } catch (error) {
    errorMessage.value = readableError(error, '保存标准答案失败')
  } finally {
    saving.value = false
  }
}

async function runEvaluation() {
  if (!props.projectId || !latestAiReview.value || !reviewDiff.value) return
  if (latestAiReview.value.reviewTaskId !== reviewDiff.value.id) {
    errorMessage.value = '最近一次 AI 审查不属于当前 Diff，请先重新执行审查'
    return
  }
  running.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await projectApi.runReviewEvaluation(
      props.projectId,
      datasetVersion.value.trim(),
      latestAiReview.value.id,
    )
    successMessage.value = `${result.executionMode} 评测完成，F1 ${formatScore(result.f1)}`
    await loadDataset()
  } catch (error) {
    errorMessage.value = readableError(error, '执行评测失败')
  } finally {
    running.value = false
  }
}

function formatScore(value: number) {
  return Number(value ?? 0).toFixed(3)
}

function formatLatency(value: number) {
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`
}

function toolSuccessRate(run: ReviewEvaluationRun) {
  if (!run.toolCallCount) return '—'
  return `${Math.round((run.toolSuccessCount / run.toolCallCount) * 100)}%`
}

function latestRun(mode: 'FIXED' | 'AGENT') {
  return mode === 'FIXED' ? latestFixedRun.value : latestAgentRun.value
}

watch(
  () => [props.open, props.projectId] as const,
  ([open]) => {
    if (open) void loadWorkspace()
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal evaluation-modal" role="dialog" aria-modal="true" aria-labelledby="evaluation-title">
      <header class="modal-header">
        <div>
          <p class="eyebrow">REPRODUCIBLE EVALUATION</p>
          <h2 id="evaluation-title">{{ projectName }} · 效果评测</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="successMessage" class="notice success" role="status">{{ successMessage }}</div>
      <div v-if="loading" class="source-loading">正在读取当前 Diff、标准答案和历史评测…</div>

      <template v-else-if="reviewDiff">
        <section class="evaluation-toolbar">
          <label>
            <span>评测集版本</span>
            <input v-model="datasetVersion" data-testid="dataset-version" maxlength="64" pattern="[A-Za-z0-9._-]+" />
          </label>
          <div>
            <small>当前 Diff</small>
            <code>{{ reviewDiff.id }} · {{ reviewDiff.targetRevision?.slice(0, 10) }}</code>
          </div>
          <button class="button secondary" type="button" @click="loadDataset">加载版本</button>
        </section>

        <section class="evaluation-run-panel">
          <div>
            <b>评测最近一次已完成的 AI 审查</b>
            <p v-if="latestAiReview">
              {{ latestAiReview.executionMode }} · {{ latestAiReview.status }} · {{ latestAiReview.modelName }} · {{ latestAiReview.totalTokens }} Token
            </p>
            <p v-else>还没有可评测的 AI 审查，请先在“AI审查”中运行一种模式。</p>
          </div>
          <button
            data-testid="run-evaluation"
            class="button primary"
            type="button"
            :disabled="running || !canRunEvaluation"
            @click="runEvaluation"
          >{{ running ? '计算中…' : '评测最近审查' }}</button>
        </section>

        <section class="evaluation-comparison" aria-label="FIXED 与 AGENT 对比">
          <article v-for="mode in ['FIXED', 'AGENT'] as const" :key="mode" class="evaluation-score-card">
            <template v-if="latestRun(mode)">
              <div class="evaluation-mode-head">
                <b>{{ mode }}</b>
                <span v-if="latestRun(mode)?.partialMetrics">部分指标</span>
              </div>
              <strong>{{ formatScore(latestRun(mode)!.f1) }}</strong>
              <small>F1 SCORE</small>
              <dl>
                <div><dt>Precision</dt><dd>{{ formatScore(latestRun(mode)!.precision) }}</dd></div>
                <div><dt>Recall</dt><dd>{{ formatScore(latestRun(mode)!.recall) }}</dd></div>
                <div><dt>TP / FP / FN</dt><dd>{{ latestRun(mode)!.truePositives }} / {{ latestRun(mode)!.falsePositives }} / {{ latestRun(mode)!.falseNegatives }}</dd></div>
                <div><dt>Token / 耗时</dt><dd>{{ latestRun(mode)!.totalTokens }} / {{ formatLatency(latestRun(mode)!.latencyMs) }}</dd></div>
                <div><dt>Tool 成功率</dt><dd>{{ toolSuccessRate(latestRun(mode)!) }}</dd></div>
                <div><dt>人工复核</dt><dd>{{ latestRun(mode)!.manualReviewCount }}</dd></div>
              </dl>
            </template>
            <template v-else>
              <div class="evaluation-mode-head"><b>{{ mode }}</b></div>
              <div class="evaluation-missing">
                <b>暂无运行快照</b>
                <p>先在 AI 审查页运行 {{ mode }}，再回来评测最近审查。</p>
              </div>
            </template>
          </article>
        </section>

        <section class="evaluation-cases">
          <div class="evaluation-section-title">
            <div><b>固定标准答案</b><small>首次运行后版本冻结；修改标准时创建新版本</small></div>
            <span>{{ cases.length }} 条</span>
          </div>
          <div v-if="!cases.length" class="source-empty compact">
            <b>当前版本还没有标准答案</b><span>先录入已人工确认的缺陷，或添加一个无缺陷对照。</span>
          </div>
          <div v-else class="evaluation-case-list">
            <article v-for="item in cases" :key="item.id">
              <span :class="item.expectationType.toLowerCase()">{{ item.expectationType }}</span>
              <div>
                <b>{{ item.name }}</b>
                <code v-if="item.expectationType === 'DEFECT'">{{ item.category }} · {{ item.filePath }}:{{ item.startLine }}–{{ item.endLine }}</code>
                <small>{{ item.caseKey }} · {{ item.rationale }}</small>
              </div>
            </article>
          </div>
        </section>

        <form class="evaluation-case-form" @submit.prevent="createCase">
          <div class="evaluation-section-title">
            <div><b>录入标准答案</b><small>这里只保存位置和依据，不复制完整源码</small></div>
          </div>
          <div class="evaluation-form-grid">
            <label><span>用例键</span><input v-model="form.caseKey" required maxlength="100" pattern="[A-Za-z0-9._-]+" placeholder="lost-update-01" /></label>
            <label><span>名称</span><input v-model="form.name" required maxlength="200" placeholder="库存丢失更新" /></label>
            <label><span>期望类型</span><select v-model="form.expectationType"><option value="DEFECT">缺陷</option><option value="CLEAN">无缺陷对照</option></select></label>
            <label v-if="form.expectationType === 'DEFECT'"><span>类别</span><select v-model="form.category"><option v-for="category in categoryOptions" :key="category">{{ category }}</option></select></label>
            <label v-if="form.expectationType === 'DEFECT'" class="wide"><span>相对文件路径</span><input v-model="form.filePath" required maxlength="1000" placeholder="src/main/java/.../OrderService.java" /></label>
            <label v-if="form.expectationType === 'DEFECT'"><span>起始行</span><input v-model.number="form.startLine" type="number" min="1" required /></label>
            <label v-if="form.expectationType === 'DEFECT'"><span>结束行</span><input v-model.number="form.endLine" type="number" min="1" required /></label>
            <label class="wide"><span>人工标注依据</span><textarea v-model="form.rationale" required maxlength="1000" rows="2" placeholder="说明为什么这是确定缺陷或无缺陷对照" /></label>
          </div>
          <div class="modal-actions">
            <button class="button primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '加入评测集' }}</button>
          </div>
        </form>
      </template>

      <div v-else-if="!loading" class="state-box compact">
        <h3>还没有可评测的 Diff</h3>
        <p>请先为项目生成成功的 Diff，再建立固定标准答案。</p>
      </div>
    </section>
  </div>
</template>
