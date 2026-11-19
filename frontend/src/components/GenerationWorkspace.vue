<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useModelAccess } from '../composables/useModelAccess'
import GeneratedProjectView from './GeneratedProjectView.vue'
import { ApiError } from '../services/apiClient'
import { generationApi } from '../services/generationApi'
import type {
  ClarificationAnswerForm,
  GenerationSession,
  RequirementAnswer,
  RequirementDecisionMode,
  RequirementQuestion,
} from '../types/generation'

interface AnswerDraft {
  decisionMode?: RequirementDecisionMode
  selectedOptionIds: string[]
  customAnswer: string
  legacyAnswer: string
  customOpen: boolean
}

const requirement = ref('')
const ensureModel = useModelAccess()
const history = ref<GenerationSession[]>([])
const historyError = ref('')
const session = ref<GenerationSession | null>(null)
const answerDrafts = reactive<Record<string, AnswerDraft>>({})
const loading = ref(false)
const errorMessage = ref('')
let epoch = 0
let thinkingTimer: ReturnType<typeof setTimeout> | undefined
const modern = computed(() => Boolean(session.value?.latestSpec.stage && session.value.latestSpec.stage !== 'LEGACY'))
const pendingQuestions = computed(() => session.value?.latestSpec.questions.filter(question => !modern.value || !currentAnswer(question.id)) ?? [])
const stageLabel = computed(() => ({ BUSINESS: '先聊聊项目做什么', LANGUAGE: '选择编程语言', TECHNOLOGY: '选择架构与框架', DETAILS: '补齐存储和运行方式', REVIEW: '核对最终方案' }[session.value?.latestSpec.stage ?? ''] ?? '澄清需求'))
onMounted(() => { void loadHistory() })
onBeforeUnmount(() => { epoch++; clearTimeout(thinkingTimer) })

async function loadHistory() {
  const ticket = epoch
  try { const items = await generationApi.list(); if (ticket === epoch) { history.value = items; historyError.value = '' } }
  catch { if (ticket === epoch) historyError.value = '历史暂时无法读取，可重试；不会影响新建项目。' }
}
async function resumeSession(id: string) {
  if (loading.value || !id) return
  const ticket = ++epoch
  clearTimeout(thinkingTimer)
  loading.value = true; errorMessage.value = ''
  try {
    const saved = await generationApi.get(id)
    if (ticket !== epoch) return
    session.value = saved; syncAnswers()
    if (saved.status === 'THINKING') watchThinking(id, ticket)
  } catch (error) { if (ticket === epoch) showError(error) }
  finally { if (ticket === epoch) loading.value = false }
}
function watchThinking(id: string, ticket: number, attempts = 0) {
  thinkingTimer = setTimeout(async () => {
    if (ticket !== epoch) return
    try {
      const saved = await generationApi.get(id)
      if (ticket !== epoch) return
      session.value = saved
      if (saved.status === 'THINKING' && attempts < 300) watchThinking(id, ticket, attempts + 1)
      else syncAnswers()
    } catch (error) { if (ticket === epoch) showError(error) }
  }, 2000)
}

const canSubmitAnswers = computed(() => {
  if (!session.value || session.value.status !== 'CLARIFYING') return false
  return pendingQuestions.value.length > 0 && pendingQuestions.value
    .filter((question) => question.required)
    .every((question) => isAnswered(question))
})

function emptyDraft(): AnswerDraft {
  return {
    selectedOptionIds: [],
    customAnswer: '',
    legacyAnswer: '',
    customOpen: false,
  }
}

function draftFor(questionId: string): AnswerDraft {
  if (!answerDrafts[questionId]) answerDrafts[questionId] = emptyDraft()
  return answerDrafts[questionId]
}

function isLegacyQuestion(question: RequirementQuestion) {
  return question.legacy === true || !question.inputType
}

function isAnswered(question: RequirementQuestion) {
  const draft = draftFor(question.id)
  if (isLegacyQuestion(question)) return Boolean(draft.legacyAnswer.trim())
  if (question.inputType === 'FREE_TEXT') return Boolean(draft.customAnswer.trim())
  if (draft.decisionMode === 'CUSTOM') return Boolean(draft.customAnswer.trim())
  if (!draft.decisionMode || draft.selectedOptionIds.length === 0) return false
  return question.inputType !== 'SINGLE_CHOICE' || draft.selectedOptionIds.length === 1
}

function categoryLabel(question: RequirementQuestion) {
  if (!question.category) return '历史问题'
  return {
    BUSINESS: '业务问题',
    TECHNICAL: '工程规则',
    TRADEOFF: '重要取舍',
  }[question.category]
}

function decisionLabel(answer?: RequirementAnswer) {
  if (!answer) return ''
  if (!answer.decisionMode || answer.decisionMode === 'LEGACY_TEXT') return '文本回答'
  return {
    USER_SELECTED: '用户选择',
    USER_ACCEPTED_RECOMMENDATION: '已采用 AI 推荐',
    AI_DEFAULTED: '由 AI 决定',
    CUSTOM: '自定义方案',
  }[answer.decisionMode]
}

function currentAnswer(questionId: string) {
  return session.value?.latestSpec.answers.find((answer) => answer.questionId === questionId)
}

function selectSingle(question: RequirementQuestion, optionId: string) {
  const draft = draftFor(question.id)
  draft.selectedOptionIds = [optionId]
  draft.decisionMode = 'USER_SELECTED'
}

function toggleMultiple(question: RequirementQuestion, optionId: string, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  const draft = draftFor(question.id)
  const selected = new Set(draft.selectedOptionIds)
  if (checked) selected.add(optionId)
  else selected.delete(optionId)
  draft.selectedOptionIds = [...selected]
  draft.decisionMode = selected.size > 0 ? 'USER_SELECTED' : undefined
}

function applyRecommendation(
  question: RequirementQuestion,
  mode: 'USER_ACCEPTED_RECOMMENDATION' | 'AI_DEFAULTED',
) {
  const draft = draftFor(question.id)
  draft.selectedOptionIds = (question.options ?? [])
    .filter((option) => option.recommended)
    .map((option) => option.id)
  draft.decisionMode = mode
  draft.customAnswer = ''
  draft.customOpen = false
}

function toggleCustom(question: RequirementQuestion) {
  const draft = draftFor(question.id)
  draft.customOpen = !draft.customOpen
  if (!draft.customOpen && !draft.customAnswer.trim()) return
  if (draft.selectedOptionIds.length === 0) draft.decisionMode = 'CUSTOM'
}

function updateCustomAnswer(question: RequirementQuestion, event: Event) {
  const draft = draftFor(question.id)
  draft.customAnswer = (event.target as HTMLTextAreaElement).value
  if (question.inputType === 'FREE_TEXT' || draft.selectedOptionIds.length === 0) {
    draft.decisionMode = draft.customAnswer.trim() ? 'CUSTOM' : undefined
  } else {
    draft.decisionMode = 'USER_SELECTED'
  }
}

function showError(error: unknown) {
  errorMessage.value = error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

async function createSession() {
  if (loading.value) return
  if (!requirement.value.trim()) {
    errorMessage.value = '请先用一句话描述你想创建的项目'
    return
  }
  loading.value = true
  const ticket = epoch
  errorMessage.value = ''
  try {
    if (!await ensureModel('AI 需求追问')) return
    if (ticket !== epoch) return
    const created = await generationApi.create(requirement.value.trim())
    if (ticket !== epoch) return
    session.value = created
    syncAnswers()
    void loadHistory()
  } catch (error) {
    if (ticket === epoch) showError(error)
  } finally {
    if (ticket === epoch) loading.value = false
  }
}

function toRequest(question: RequirementQuestion): ClarificationAnswerForm | null {
  const draft = draftFor(question.id)
  if (isLegacyQuestion(question)) {
    return draft.legacyAnswer.trim()
      ? { questionId: question.id, answer: draft.legacyAnswer.trim() }
      : null
  }
  if (!isAnswered(question)) return null
  return {
    questionId: question.id,
    decisionMode: draft.decisionMode,
    selectedOptionIds: [...draft.selectedOptionIds],
    customAnswer: draft.customAnswer.trim(),
  }
}

async function submitAnswers() {
  if (loading.value || !session.value || !canSubmitAnswers.value) return
  const ticket = epoch
  const selected = session.value
  loading.value = true
  errorMessage.value = ''
  try {
    if (!await ensureModel('下一轮 AI 追问')) return
    if (ticket !== epoch) return
    const answers = pendingQuestions.value
      .map(toRequest)
      .filter((answer): answer is ClarificationAnswerForm => answer !== null)
    const revised = modern.value
      ? await generationApi.clarify(selected.id, answers, selected.latestSpec.id)
      : await generationApi.clarify(selected.id, answers)
    if (ticket !== epoch) return
    session.value = revised
    syncAnswers()
    void loadHistory()
  } catch (error) {
    if (ticket === epoch) showError(error)
  } finally {
    if (ticket === epoch) loading.value = false
  }
}

async function confirmSpec() {
  if (loading.value || !session.value) return
  const ticket = epoch
  loading.value = true
  errorMessage.value = ''
  try {
    const confirmed = await generationApi.confirm(session.value.id, session.value.latestSpec.id)
    if (ticket !== epoch) return
    session.value = confirmed
    void loadHistory()
  } catch (error) {
    if (ticket === epoch) showError(error)
  } finally {
    if (ticket === epoch) loading.value = false
  }
}

function startOver() {
  epoch++; clearTimeout(thinkingTimer); loading.value = false
  session.value = null
  requirement.value = ''
  Object.keys(answerDrafts).forEach((key) => delete answerDrafts[key])
  errorMessage.value = ''
}

function syncAnswers() {
  if (!session.value) return
  Object.keys(answerDrafts).forEach((key) => delete answerDrafts[key])
  session.value.latestSpec.questions.forEach((question) => {
    answerDrafts[question.id] = emptyDraft()
  })
  session.value.latestSpec.answers.forEach((answer) => {
    const draft = draftFor(answer.questionId)
    draft.decisionMode = answer.decisionMode ?? 'LEGACY_TEXT'
    draft.selectedOptionIds = [...(answer.selectedOptionIds ?? [])]
    draft.customAnswer = answer.customAnswer ?? ''
    draft.legacyAnswer = answer.decisionMode && answer.decisionMode !== 'LEGACY_TEXT'
      ? ''
      : answer.answer
    draft.customOpen = Boolean(draft.customAnswer)
  })
}
</script>

<template>
  <section class="generation-workspace">
    <header class="topbar generation-heading">
      <div>
        <p class="eyebrow">BUILD / YOUR NEXT PROJECT</p>
        <h1>从一个想法，做出你的项目</h1>
        <p>说说你想做什么，AI 会逐轮给出选择题，帮你选语言、架构、框架，再生成源码。</p>
      </div>
      <button v-if="session" class="button secondary" type="button" :disabled="loading" @click="startOver">新建另一个项目</button>
    </header>

    <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
    <div v-if="historyError" class="notice warning">{{ historyError }} <button type="button" @click="loadHistory">重新读取</button></div>
    <label v-if="history.length" class="generation-history">继续之前的项目
      <select :value="session?.id ?? ''" :disabled="loading" aria-label="继续之前的项目" @change="resumeSession(($event.target as HTMLSelectElement).value)">
        <option value="" disabled>选择一个已保存的项目想法</option>
        <option v-for="item in history" :key="item.id" :value="item.id">{{ item.originalRequirement.slice(0, 55) }} · {{ item.status === 'CONFIRMED' ? '方案已确认' : '第' + item.latestVersionNo + '轮' }}</option>
      </select>
    </label>

    <section v-if="!session" class="generation-entry panel">
      <div class="generation-step"><span>01</span><b>描述目标</b><small>一句话即可开始，Agent 会继续追问。</small></div>
      <form @submit.prevent="createSession">
        <label for="project-requirement">你想做一个什么项目？</label>
        <textarea
          id="project-requirement"
          v-model="requirement"
          maxlength="2000"
          rows="5"
          placeholder="例如：做一个供企业内部使用的设备维修工单系统"
        ></textarea>
        <p>网页、命令行工具、管理系统等都可以。每轮 AI 分析会使用当前连接的 API 额度；先完成选择，再确认生成。</p>
        <button class="button primary" type="submit" :disabled="loading">
          {{ loading ? 'AI 正在理解想法，请稍候…' : '开始聊聊这个项目 · 使用 API 额度' }}
        </button>
      </form>
    </section>

    <template v-else>
      <section class="generation-progress" aria-label="生成进度">
        <span class="done">1. 描述目标</span>
        <span :class="{ done: session.latestVersionNo > 1 }">2. 澄清需求</span>
        <span :class="{ done: session.status === 'CONFIRMED' }">3. 确认方案</span>
        <span>4. 生成工程</span>
      </section>

      <div class="generation-layout">
        <details class="panel generation-spec" :open="session.latestSpec.readyForGeneration || session.status === 'CONFIRMED'">
          <summary class="generation-section-heading">
            <div><small>已保存的需求方案 · 点击展开或收起</small><h2>查看当前方案 · 版本 {{ session.latestSpec.versionNo }}</h2></div>
            <span class="status" :class="session.status.toLowerCase()"><i></i>{{ session.status === 'CONFIRMED' ? '已确认' : session.status === 'THINKING' ? '整理中' : '待澄清' }}</span>
          </summary>
          <article>
            <h3>AI 对需求的理解</h3>
            <p class="preserve-lines">{{ session.latestSpec.requirementSummary }}</p>
          </article>
          <article>
            <h3>AI 推荐的架构方案</h3>
            <p class="preserve-lines">{{ session.latestSpec.architectureSummary }}</p>
          </article>
          <article>
            <h3>尚未确认的假设</h3>
            <ul><li v-for="assumption in session.latestSpec.assumptions" :key="assumption">{{ assumption }}</li></ul>
          </article>
        </details>

        <section class="panel generation-questions">
          <div class="generation-section-heading">
            <div><small>反向提问 · 第{{ session.latestVersionNo }}轮</small><h2>{{ stageLabel }}</h2></div>
          </div>
          <div v-if="session.status === 'CONFIRMED'" class="generation-confirmed">
            <b>方案已经确认</b>
            <p>将按这个固定版本生成独立的项目源码，不会覆盖已有项目。</p>
          </div>
          <div v-else-if="session.status === 'THINKING'" class="generation-confirmed" role="status"><span class="loader"></span><p>AI 正在根据上一轮选择整理方案，完成后会自动更新。</p><button class="button secondary" type="button" @click="resumeSession(session.id)">刷新进度</button></div>
          <div v-else-if="modern && session.latestSpec.readyForGeneration" class="generation-confirmed">
            <b>选择已齐全，先核对上方方案</b><p>确认后可以生成源码；如果想改变目标或技术方案，可新建另一个项目想法。</p>
            <button class="button primary" type="button" :disabled="loading" @click="confirmSpec">确认方案，进入生成</button>
          </div>
          <form v-else @submit.prevent="submitAnswers">
            <p v-if="session.lastError" class="notice warning">{{ session.lastError }}</p>
            <fieldset :disabled="loading" class="generation-question-fields">
            <article
              v-for="(question, questionIndex) in pendingQuestions"
              :key="question.id"
              class="requirement-question-card"
            >
              <header>
                <span class="question-index">{{ String(questionIndex + 1).padStart(2, '0') }}</span>
                <span class="question-category">{{ categoryLabel(question) }}</span>
                <b v-if="question.required">必答</b>
              </header>
              <h3>{{ question.question }}</h3>
              <p class="question-reason">为什么要问：{{ question.reason }}</p>

              <aside v-if="question.aiRecommendation" class="ai-recommendation">
                <strong>AI 建议：{{ question.aiRecommendation }}</strong>
                <p>{{ question.recommendationReason }}</p>
              </aside>

              <textarea
                v-if="isLegacyQuestion(question)"
                v-model="draftFor(question.id).legacyAnswer"
                maxlength="2000"
                rows="3"
                aria-label="历史问题回答"
              ></textarea>

              <textarea
                v-else-if="question.inputType === 'FREE_TEXT'"
                :value="draftFor(question.id).customAnswer"
                maxlength="2000"
                rows="3"
                placeholder="可选补充；没有外部服务需求可以留空"
                aria-label="自定义回答"
                @input="updateCustomAnswer(question, $event)"
              ></textarea>

              <div v-else class="requirement-options">
                <label
                  v-for="option in question.options"
                  :key="option.id"
                  class="requirement-option"
                  :class="{ selected: draftFor(question.id).selectedOptionIds.includes(option.id) }"
                >
                  <input
                    v-if="question.inputType === 'SINGLE_CHOICE'"
                    type="radio"
                    :name="`question-${question.id}`"
                    :checked="draftFor(question.id).selectedOptionIds.includes(option.id)"
                    @change="selectSingle(question, option.id)"
                  />
                  <input
                    v-else
                    type="checkbox"
                    :checked="draftFor(question.id).selectedOptionIds.includes(option.id)"
                    @change="toggleMultiple(question, option.id, $event)"
                  />
                  <span>
                    <strong>{{ option.label }} <em v-if="option.recommended">AI 推荐</em></strong>
                    <small>{{ option.description }}</small>
                    <small class="option-impact">代码影响：{{ option.impact }}</small>
                  </span>
                </label>
              </div>

              <div v-if="question.inputType && question.inputType !== 'FREE_TEXT'" class="recommendation-actions">
                <button type="button" @click="applyRecommendation(question, 'USER_ACCEPTED_RECOMMENDATION')">
                  采用 AI 推荐方案
                </button>
                <button type="button" @click="applyRecommendation(question, 'AI_DEFAULTED')">
                  我不确定，由 AI 决定
                </button>
                <button v-if="question.allowCustomAnswer" type="button" @click="toggleCustom(question)">
                  自定义方案 / 补充
                </button>
              </div>

              <textarea
                v-if="question.inputType !== 'FREE_TEXT' && draftFor(question.id).customOpen"
                :value="draftFor(question.id).customAnswer"
                maxlength="2000"
                rows="2"
                placeholder="可在现有选择上补充，或不选预设项并填写完整自定义方案"
                aria-label="选项补充说明"
                @input="updateCustomAnswer(question, $event)"
              ></textarea>

              <p v-if="currentAnswer(question.id)" class="current-decision">
                <b>{{ decisionLabel(currentAnswer(question.id)) }}</b>
                {{ currentAnswer(question.id)?.answer }}
              </p>
            </article>
            </fieldset>

            <div class="generation-actions">
              <button class="button secondary" type="submit" :disabled="loading || !canSubmitAnswers">
                {{ loading ? 'AI 正在整理下一轮…' : '继续，看看 AI 的下一步建议 · 使用 API 额度' }}
              </button>
              <button
                v-if="!modern && session.latestVersionNo > 1"
                class="button primary"
                type="button"
                :disabled="loading"
                @click="confirmSpec"
              >确认这个方案</button>
            </div>
          </form>
        </section>
      </div>
      <details v-if="modern && session.latestSpec.answers.length" class="generation-decisions panel"><summary>已保存的选择（{{ session.latestSpec.answers.length }}）</summary><p v-for="answer in session.latestSpec.answers" :key="answer.questionId"><b>{{ session.latestSpec.questions.find(question => question.id === answer.questionId)?.question }}</b><br>{{ answer.answer }}</p></details>
      <GeneratedProjectView v-if="session.status === 'CONFIRMED'" :key="session.id" :session-id="session.id" :version-id="session.latestSpec.id" />
    </template>
  </section>
</template>

<style scoped>
.generation-layout { grid-template-columns: minmax(0, 1fr); }
.generation-spec > summary { cursor: pointer; list-style: none; }
.generation-spec > summary::-webkit-details-marker { display: none; }
.generation-spec:not([open]) > summary { border-bottom: 0; border-radius: 20px; }
.generation-spec > summary h2 { font-size: 16px; }
.requirement-question-card h3 { font-size: 16px; }
.question-reason, .requirement-option small { font-size: 12px; line-height: 1.7; }
.requirement-option strong { font-size: 14px; }
.generation-history { display: flex; align-items: center; gap: 14px; margin: 20px 0; font-size: 13px; color: var(--ink-soft); }
.generation-history select { min-width: 0; max-width: 600px; padding: 10px 12px; border: 1px solid var(--line); background: var(--surface); }
.generation-question-fields { border: 0; margin: 0; padding: 0; display: grid; gap: 18px; min-width: 0; }
.generation-decisions { margin-top: 20px; padding: 18px 20px; font-size: 13px; }
.generation-decisions summary { cursor: pointer; font-weight: 700; }
.generation-decisions p { color: var(--ink-soft); line-height: 1.8; }
.generation-confirmed .button { margin-top: 14px; }
.generation-spec p, .generation-spec li { font-size: 13px; }
@media(max-width: 600px) { .generation-history { align-items: stretch; flex-direction: column; } }
</style>
