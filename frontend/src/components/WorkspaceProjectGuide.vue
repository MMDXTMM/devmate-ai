<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ApiError, projectApi } from '../services/projectApi'
import type { Project, ProjectUnderstandingReport } from '../types/project'
import ProjectGuideView from './ProjectGuideView.vue'
import { useModelAccess } from '../composables/useModelAccess'
import { projectEmoji } from '../utils/guideAppearance'

const requireModel = useModelAccess()
const props = defineProps<{ project: Project }>()
const emit = defineEmits<{ chat: [question: string]; configureModel: [] }>()
const report = ref<ProjectUnderstandingReport | null>(null)
const loading = ref(false)
const generating = ref(false)
const waiting = ref(false)
const error = ref('')
let epoch = 0
let attemptKey: string | undefined
let timer: ReturnType<typeof setTimeout> | undefined
let polls = 0
const MAX_POLLS = 220

function clearTimer() {
  if (timer) clearTimeout(timer)
  timer = undefined
}
function current(generation: number) {
  return generation === epoch
}
function belongs(item: ProjectUnderstandingReport) {
  return (
    item.projectId === props.project.id &&
    item.revision === props.project.currentRevision
  )
}
function schedule(generation: number) {
  clearTimer()
  waiting.value = true
  if (polls++ >= MAX_POLLS) {
    waiting.value = false
    error.value = '导览仍在后台生成，请稍后点击“刷新导览”。无需重复生成。'
    return
  }
  timer = setTimeout(() => {
    void load(false, generation)
  }, 3000)
}
function accept(item: ProjectUnderstandingReport, generation: number) {
  if (!current(generation) || !belongs(item)) return false
  if (item.status === 'RUNNING') {
    schedule(generation)
    return true
  }
  clearTimer()
  waiting.value = false
  if (item.status === 'FAILED') {
    error.value = item.errorMessage || '导览生成失败，请检查模型连接后重试'
    attemptKey = undefined
  } else {
    report.value = item
    attemptKey = undefined
  }
  return true
}
async function generate(generation = epoch) {
  if (
    !current(generation) ||
    generating.value ||
    !props.project.currentRevision
  )
    return
  clearTimer()
  polls = 0
  waiting.value = false
  generating.value = true
  error.value = ''
  attemptKey ??= crypto.randomUUID()
  const projectId = props.project.id
  const revision = props.project.currentRevision
  try {
    if (!await requireModel('生成项目概要与新人导览') || !current(generation)) return
    const item = await projectApi.createUnderstandingReport(
      projectId,
      revision,
      attemptKey,
    )
    if (!current(generation)) return
    if (!accept(item, generation))
      error.value = '源码版本已变化，请刷新项目后查看导览'
  } catch (cause) {
    if (!current(generation)) return
    error.value =
      cause instanceof ApiError ? cause.message : '项目导览生成失败，请重试'
    // Response lost: keep the key. Explicit server failure: next user retry may start a fresh task.
    if (cause instanceof ApiError && cause.status) attemptKey = undefined
    if (cause instanceof ApiError && cause.status === 409) {
      const conflict = error.value
      await load(false, generation)
      if (current(generation) && !waiting.value) error.value = conflict
    }
  } finally {
    if (current(generation)) generating.value = false
  }
}
async function load(allowInitialGeneration: boolean, generation = epoch) {
  if (
    !current(generation) ||
    !props.project.currentRevision ||
    props.project.status !== 'READY'
  )
    return
  loading.value = true
  error.value = ''
  try {
    const item = await projectApi.latestUnderstandingReport(props.project.id)
    if (!current(generation)) return
    if (!belongs(item)) {
      if (allowInitialGeneration) await generate(generation)
      else {
        waiting.value = false
        error.value = '上次导览对应其他源码版本，请为当前版本生成导览'
      }
      return
    }
    accept(item, generation)
    // Keep the old guide visible while filling the newly introduced learning overview once.
    if (
      allowInitialGeneration &&
      item.status === 'SUCCEEDED' &&
      !item.guide?.content.overview
    ) {
      await generate(generation)
    }
  } catch (cause) {
    if (!current(generation)) return
    if (
      cause instanceof ApiError &&
      cause.status === 404 &&
      allowInitialGeneration
    )
      await generate(generation)
    else {
      waiting.value = false
      error.value =
        cause instanceof ApiError ? cause.message : '导览读取失败，请刷新重试'
    }
  } finally {
    if (current(generation)) loading.value = false
  }
}
watch(
  () => [props.project.id, props.project.currentRevision, props.project.status],
  () => {
    epoch++
    clearTimer()
    polls = 0
    attemptKey = undefined
    report.value = null
    loading.value = false
    generating.value = false
    waiting.value = false
    error.value = ''
    void load(true, epoch)
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  epoch++
  clearTimer()
})
</script>

<template>
  <section
    class="workspace-project-guide"
    aria-labelledby="project-overview-title"
    :aria-busy="loading || generating || waiting"
  >
    <header class="workspace-guide-header">
      <div>
        <p class="eyebrow">从这里开始</p>
        <h2 id="project-overview-title">项目概要 · 新人导览 <span aria-hidden="true">{{ projectEmoji(project, report?.guide?.content.appearance) }}</span></h2>
        <p>优先阅读 README，再结合源码认识用途、语言和模块，了解值得学习的设计。</p>
      </div>
      <div class="workspace-guide-controls">
        <button
          class="button secondary"
          type="button"
          :disabled="loading || generating"
          @click="load(false)"
        >
          刷新导览
        </button>
        <button
          class="button secondary"
          type="button"
          :disabled="
            loading || generating || waiting || project.status !== 'READY'
          "
          @click="generate()"
        >
          {{ generating ? '正在整理…' : '重新分析' }}
        </button>
      </div>
    </header>
    <div
      v-if="generating || waiting || (loading && !report)"
      class="guide-progress"
      role="status"
    >
      <span class="loader"></span>
      <div>
        <b>{{
          generating || waiting
            ? '后台正在阅读项目并整理新人导览'
            : '正在读取项目概要'
        }}</b>
        <p>
          {{
            generating || waiting
              ? '分析通常需要 1～3 分钟，任务较多时还需排队；结果会保存并自动显示。无需重复点击，离开页面也不会取消任务。分析会使用当前账户的模型额度。'
              : '已有导览会直接展示，无需再次调用模型。'
          }}
        </p>
      </div>
    </div>
    <div v-if="error" class="notice error" role="alert">
      <p>{{ error }}</p>
      <div class="workspace-guide-controls">
        <button
          class="button secondary"
          type="button"
          :disabled="loading || generating"
          @click="load(false)"
        >
          刷新状态
        </button>
        <button
          class="button secondary"
          type="button"
          :disabled="loading || generating || waiting"
          @click="generate()"
        >
          重试生成
        </button>
        <button
          class="button secondary"
          type="button"
          @click="emit('configureModel')"
        >
          检查大模型连接
        </button>
      </div>
    </div>
    <ProjectGuideView
      v-if="report?.guide"
      :guide="report.guide"
      @chat="emit('chat', $event)"
    />
    <section v-else class="workspace-guide-placeholder">
      <h3>
        {{ report?.executiveSummary || project.description || project.name }}
      </h3>
      <p>这里将展示项目用途、语言、主要模块、值得关注的问题与学习价值。</p>
      <p v-if="project.status !== 'READY'">
        请先完成项目导入，才能根据源码整理概要。
      </p>
      <p v-else-if="!loading && !generating && !waiting">
        尚无新版导览。配置模型后可重试，已有项目数据仍保留。
      </p>
    </section>
    <p v-if="report?.guide" class="workspace-guide-saved">
      已保存 · {{ report.modelName }} ·
      {{ report.promptVersion }}。重新分析会再次使用 API 额度。
    </p>
  </section>
</template>

<style scoped>
.workspace-project-guide {
  margin: 24px 0;
  padding: 24px;
  border: 1px solid #e5dfeb;
  border-radius: 26px;
  background: #fffefa;
  min-width: 0;
}
.workspace-guide-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 20px;
}
.workspace-guide-header h2 {
  margin: 4px 0 8px;
}
.workspace-guide-header p {
  color: #62736b;
}
.workspace-guide-controls {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.guide-progress {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #f1f6f3;
  border-radius: 12px;
  padding: 18px;
  margin-bottom: 16px;
}
.guide-progress p {
  font-size: 13px;
  line-height: 1.7;
  margin: 6px 0 0;
  color: #62736b;
}
.workspace-guide-placeholder {
  padding: 24px;
  background: #f5f8f6;
  border-radius: 16px;
}
.workspace-guide-placeholder h3 {
  line-height: 1.6;
}
.workspace-guide-placeholder p,
.workspace-guide-saved {
  color: #66756e;
  line-height: 1.7;
  font-size: 13px;
}
.workspace-guide-saved {
  margin-top: 16px;
}
.notice p {
  margin-top: 0;
}
@media (max-width: 760px) {
  .workspace-project-guide {
    padding: 16px;
    margin: 16px 0;
  }
  .workspace-guide-header {
    flex-direction: column;
  }
  .workspace-guide-controls {
    width: 100%;
  }
}
</style>
