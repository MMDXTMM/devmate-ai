<script setup lang="ts">
import GenerationTaskProgress from './GenerationTaskProgress.vue'
import GeneratedProjectTools from './GeneratedProjectTools.vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { generationApi } from '../services/generationApi'
import { ApiError } from '../services/apiClient'
import { useModelAccess } from '../composables/useModelAccess'
import type { GenerationRun } from '../types/generation'

const props = defineProps<{ sessionId: string; versionId: string }>()
const ensureModel = useModelAccess()
const run = ref<GenerationRun | null>(null)
const loading = ref(false)
const reading = ref(true)
const downloading = ref(false)
const error = ref('')
const fileError = ref('')
const selectedPath = ref('')
const source = ref('')
const fileLoading = ref(false)
const pollingStopped = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let alive = true
let fileTicket = 0
let attemptKey = ''
const message = (value: unknown) => value instanceof ApiError ? value.message : '操作失败，请稍后重试'
onMounted(() => { void refresh() })
onBeforeUnmount(() => { alive = false; fileTicket++; clearTimeout(timer) })

async function refresh(attempts = 0) {
  clearTimeout(timer)
  reading.value = true
  try {
    const saved = await generationApi.latestRun(props.sessionId)
    if (!alive) return
    run.value = saved
    error.value = ''
    pollingStopped.value = attempts >= 300
    if (saved?.status === 'RUNNING' && attempts < 300) timer = setTimeout(() => { void refresh(attempts + 1) }, 2000)
    if (saved?.status === 'SUCCEEDED' && !selectedPath.value) await readFile(saved.files.find(file => file.path === 'README.md')?.path ?? saved.files[0]?.path ?? '')
  } catch (value) { if (alive) { error.value = message(value); pollingStopped.value = true } }
  finally { if (alive) reading.value = false }
}
async function generate() {
  if (loading.value || reading.value || run.value?.status === 'RUNNING' || run.value?.status === 'SUCCEEDED') return
  loading.value = true; error.value = ''
  try {
    if (!await ensureModel('生成项目源码')) return
    if (!alive) return
    if (!attemptKey || ['FAILED','CANCELLED'].includes(run.value?.status??'')) attemptKey = crypto.randomUUID()
    const saved = await generationApi.generate(props.sessionId, props.versionId, attemptKey)
    if (!alive) return
    run.value = saved
    await refresh()
  } catch (value) { if (alive) error.value = message(value) }
  finally { if (alive) loading.value = false }
}
async function readFile(path: string) {
  if (!run.value || !path) return
  const ticket = ++fileTicket
  const runId = run.value.id
  selectedPath.value = path; source.value = ''; fileError.value = ''; fileLoading.value = true
  try {
    const file = await generationApi.file(props.sessionId, runId, path)
    if (alive && ticket === fileTicket) source.value = file.content
  } catch (value) { if (alive && ticket === fileTicket) fileError.value = message(value) }
  finally { if (alive && ticket === fileTicket) fileLoading.value = false }
}
async function download() {
  if (!run.value || downloading.value) return
  downloading.value = true; error.value = ''
  try {
    const blob = await generationApi.download(props.sessionId, run.value.id)
    if (!alive) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = `project-${run.value.id}.zip`
    document.body.append(link); link.click(); link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (value) { if (alive) error.value = message(value) }
  finally { if (alive) downloading.value = false }
}
</script>

<template>
  <section class="generated-project panel" aria-labelledby="generated-project-title">
    <header class="generation-section-heading"><div><small>YOUR PROJECT</small><h2 id="generated-project-title">项目源码</h2></div><button class="button secondary" type="button" :disabled="reading || loading" @click="refresh()">刷新进度</button></header>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div v-if="reading && !run" class="generation-delivery-state" role="status">正在读取生成记录…</div>
    <div v-else-if="!run" class="generation-delivery-state">
      <h3>方案准备好了，现在生成源码</h3>
      <p>AI 会按确认的语言、架构和功能生成独立项目，提供文件预览与 ZIP 下载。此操作使用 API 额度，可能需要几分钟。</p>
      <p>系统会检查文件路径、大小和基本交付物。生成后请按 README 在自己的环境安装依赖、运行与测试。</p>
      <button class="button primary" type="button" :disabled="loading || reading" @click="generate">{{ loading ? '正在提交…' : '生成项目源码 · 使用 API 额度' }}</button>
    </div>
    <div v-else-if="run.status === 'RUNNING'" class="generation-delivery-state" role="status"><span class="loader"></span><h3>AI 正在生成你的项目</h3><p>完成后自动展示源码。可以离开页面，之后从“继续之前的项目”查看结果，不必重复生成。</p><p v-if="pollingStopped">自动刷新已暂停，请点击“刷新进度”继续查看。</p></div>
    <div v-else-if="run.status === 'FAILED'||run.status==='CANCELLED'" class="generation-delivery-state"><h3>{{run.status==='CANCELLED'?'生成已取消':'这次没有生成成功'}}</h3><p role="alert">{{ run.errorMessage }}</p><p>已确认的方案仍然保存。重试会再次使用 API 额度。</p><button class="button primary" type="button" :disabled="loading || reading" @click="generate">{{ loading ? '正在提交…' : '重新生成 · 使用 API 额度' }}</button></div>
    <template v-else>
      <div class="generation-delivery-summary"><div><h3>源码已生成 · {{ run.files.length }} 个文件</h3><p>{{ run.summary }}</p></div><button class="button primary" type="button" :disabled="downloading" @click="download">{{ downloading ? '正在下载…' : '下载项目 ZIP' }}</button></div>
      <p class="notice warning">此处预览和 ZIP 为初次生成的原始版本，生成时尚未安装依赖或运行代码、编译和测试。后续验证结果、当前选用版本及其下载，请看下方“继续完善这个项目”。</p>
      <div class="generated-files">
        <nav aria-label="生成文件列表"><button v-for="file in run.files" :key="file.path" type="button" :class="{ active: selectedPath === file.path }" :aria-current="selectedPath === file.path ? 'true' : undefined" @click="readFile(file.path)">{{ file.path }}<small>{{ file.characters }} 字符</small></button></nav>
        <div class="generated-source"><header>{{ selectedPath || '选择文件预览' }}</header><p v-if="fileLoading" role="status">正在读取…</p><p v-else-if="fileError" role="alert">{{ fileError }} <button type="button" @click="readFile(selectedPath)">重试</button></p><pre v-else><code>{{ source }}</code></pre></div>
      </div>
      <GeneratedProjectTools :key="run.id" :session-id="sessionId" :run-id="run.id" />
      <div class="generation-run-guide"><h3>接下来怎么运行</h3><p class="preserve-lines">{{ run.runInstructions }}</p><details v-if="run.warnings.length"><summary>运行前需要知道的事项</summary><ul><li v-for="warning in run.warnings" :key="warning">{{ warning }}</li></ul></details></div>
    </template>
    <GenerationTaskProgress v-if="run" :key="run.id + run.updatedAt" :session-id="sessionId" kind="GENERATE" :reference-id="run.id" @cancelled="refresh()" />
  </section>
</template>

<style scoped>
.generated-project { margin: 24px 0; }
.generation-delivery-state, .generation-delivery-summary, .generation-run-guide { padding: 24px; }
.generation-delivery-state h3, .generation-delivery-summary h3 { margin-top: 0; font-size: 17px; }
.generation-delivery-state p, .generation-delivery-summary p, .generation-run-guide { color: var(--ink-soft); font-size: 13px; line-height: 1.9; }
.generation-delivery-summary { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.generation-delivery-summary .button { flex-shrink: 0; }
.generated-project > .notice { margin: 0 24px 20px; }
.generated-files { display: grid; grid-template-columns: minmax(190px, 260px) minmax(0, 1fr); border-block: 1px solid var(--line); }
.generated-files nav { max-height: 520px; overflow: auto; border-right: 1px solid var(--line); padding: 12px; }
.generated-files nav button { display: block; width: 100%; border: 0; background: transparent; padding: 10px; border-radius: 8px; font-size: 12px; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.generated-files nav button.active { background: var(--accent-soft); color: var(--accent); font-weight: 700; }
.generated-files nav small { display: block; margin-top: 4px; color: var(--muted); font-size: 10px; }
.generated-source { min-width: 0; }
.generated-source header { padding: 12px 16px; border-bottom: 1px solid var(--line); font-size: 12px; overflow-wrap: anywhere; }
.generated-source pre { margin: 0; padding: 20px; max-height: 480px; overflow: auto; background: var(--code-bg); color: var(--code-ink); font-size: 12px; line-height: 1.8; tab-size: 2; }
.generated-source > p { padding: 16px; font-size: 13px; }
.generation-run-guide summary { cursor: pointer; }
@media(max-width: 700px) { .generated-files { grid-template-columns: minmax(0, 1fr); } .generated-files nav { max-height: 180px; border-right: 0; border-bottom: 1px solid var(--line); } .generation-delivery-summary { flex-direction: column; align-items: stretch; } }
</style>
