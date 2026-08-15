<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
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
const session = ref<GenerationSession | null>(null)
const answerDrafts = reactive<Record<string, AnswerDraft>>({})
const loading = ref(false)
const errorMessage = ref('')

const canSubmitAnswers = computed(() => {
  if (!session.value || session.value.status !== 'CLARIFYING') return false
  return session.value.latestSpec.questions
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
  if (!requirement.value.trim()) {
    errorMessage.value = '请先用一句话描述你想创建的项目'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    session.value = await generationApi.create(requirement.value.trim())
    syncAnswers()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
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
  if (!session.value || !canSubmitAnswers.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const answers = session.value.latestSpec.questions
      .map(toRequest)
      .filter((answer): answer is ClarificationAnswerForm => answer !== null)
    session.value = await generationApi.clarify(session.value.id, answers)
    syncAnswers()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function confirmSpec() {
  if (!session.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    session.value = await generationApi.confirm(session.value.id, session.value.latestSpec.id)
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function startOver() {
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
        <p class="eyebrow">BUILD / NEW SPRING BOOT PROJECT</p>
        <h1>一句话生成 Java 项目</h1>
        <p>先理解需求和确认架构，再生成能够编译、测试和继续开发的工程。</p>
      </div>
      <button v-if="session" class="button secondary" type="button" @click="startOver">重新描述</button>
    </header>

    <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>

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
        <p>第一版支持工单、预约、库存、内容管理、商城后台等业务流程型后端。</p>
        <button class="button primary" type="submit" :disabled="loading">
          {{ loading ? '正在形成方案…' : '开始分析需求' }}
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
        <section class="panel generation-spec">
          <div class="generation-section-heading">
            <div><small>需求方案</small><h2>版本 {{ session.latestSpec.versionNo }}</h2></div>
            <span class="status" :class="session.status.toLowerCase()"><i></i>{{ session.status === 'CONFIRMED' ? '已确认' : '待澄清' }}</span>
          </div>
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
        </section>

        <section class="panel generation-questions">
          <div class="generation-section-heading">
            <div><small>反向提问</small><h2>选择最符合你的业务方案</h2></div>
          </div>
          <div v-if="session.status === 'CONFIRMED'" class="generation-confirmed">
            <b>方案已经确认</b>
            <p>下一阶段将根据这个固定版本生成 Spring Boot 工程。目前代码生成 Tool 尚未接入。</p>
          </div>
          <form v-else @submit.prevent="submitAnswers">
            <article
              v-for="(question, questionIndex) in session.latestSpec.questions"
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

            <div class="generation-actions">
              <button class="button secondary" type="submit" :disabled="loading || !canSubmitAnswers">
                {{ loading ? '正在更新…' : '更新需求方案' }}
              </button>
              <button
                v-if="session.latestVersionNo > 1"
                class="button primary"
                type="button"
                :disabled="loading"
                @click="confirmSpec"
              >确认这个方案</button>
            </div>
          </form>
        </section>
      </div>
    </template>
  </section>
</template>
