<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, reactive, ref } from 'vue'
import { projectEmoji } from './utils/guideAppearance'
import AuthView from './components/AuthView.vue'
import GenerationWorkspace from './components/GenerationWorkspace.vue'
import ProjectFormModal from './components/ProjectFormModal.vue'
import ProjectUnderstandingWorkspace from './components/ProjectUnderstandingWorkspace.vue'
import SourceStructureModal from './components/SourceStructureModal.vue'
import DiffReportModal from './components/DiffReportModal.vue'
import StaticAnalysisModal from './components/StaticAnalysisModal.vue'
import RetrievalModal from './components/RetrievalModal.vue'
import AiReviewModal from './components/AiReviewModal.vue'
import ReviewEvaluationModal from './components/ReviewEvaluationModal.vue'
import ModelConnectionModal from './components/ModelConnectionModal.vue'
import ModelSetupRequiredDialog from './components/ModelSetupRequiredDialog.vue'
import { modelAccessKey } from './composables/useModelAccess'
import AgentConversationModal from './components/AgentConversationModal.vue'
import { modelConnectionApi } from './services/modelConnectionApi'
import { ApiError, projectApi } from './services/projectApi'
import { runProjectUnderstanding } from './services/projectUnderstandingWorkflow'
import { clearAuthSession, getAuthSession, setAuthSession, subscribeAuthSession } from './services/authSession'
import type { AuthSession } from './types/auth'
import type { PageData, Project, ProjectForm, ProjectStatus, ReviewWorkflow } from './types/project'

const pageData = ref<PageData<Project>>({ page: 1, size: 10, total: 0, pages: 0, items: [] })
const query = reactive<{ name: string; status: ProjectStatus | '' }>({ name: '', status: '' })
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingProject = ref<Project | null>(null)
const errorMessage = ref('')
const successMessage = ref('')
const deletingId = ref<string | null>(null)
const importingId = ref<string | null>(null)
const workflowId = ref<string | null>(null)
const selectedProject = ref<Project | null>(null)
const retrievalInitialQuery = ref('')
const sourceProject = ref<Project | null>(null)
const diffProject = ref<Project | null>(null)
const analysisProject = ref<Project | null>(null)
const retrievalProject = ref<Project | null>(null)
const aiReviewProject = ref<Project | null>(null)
const evaluationProject = ref<Project | null>(null)
const modelConnectionsOpen = ref(false)
const modelSetupPurpose = ref('')
const modelSetupOpen = ref(false)
let checkingModel: Promise<boolean> | undefined
provide(modelAccessKey, ensureModel)
const conversationProject = ref<Project | null>(null)
const conversationInitialQuestion = ref('')
const importStage = ref('')
const reviewingId = ref<string | null>(null)
const reviewWorkflow = ref<ReviewWorkflow | undefined>()
const authSession = ref<AuthSession | null>(getAuthSession())
const workspaceArea = ref<'projects' | 'generation'>('projects')
let unsubscribeAuth: (() => void) | undefined
let reviewStatusTimer: ReturnType<typeof window.setInterval> | undefined

const hasProjects = computed(() => pageData.value.items.length > 0)
const rangeText = computed(() => {
  if (!pageData.value.total) return '0 个项目'
  const start = (pageData.value.page - 1) * pageData.value.size + 1
  const end = Math.min(pageData.value.page * pageData.value.size, pageData.value.total)
  return `${start}–${end} / ${pageData.value.total}`
})

const statusLabel: Record<ProjectStatus, string> = {
  CREATED: '待索引',
  INDEXING: '索引中',
  READY: '已就绪',
  FAILED: '失败',
}

function showError(error: unknown) {
  errorMessage.value = error instanceof ApiError ? error.message : '操作失败，请稍后重试'
  successMessage.value = ''
}

function showSuccess(message: string) {
  successMessage.value = message
  errorMessage.value = ''
  window.setTimeout(() => {
    if (successMessage.value === message) successMessage.value = ''
  }, 2500)
}

async function loadProjects(page = 1) {
  loading.value = true
  try {
    pageData.value = await projectApi.list({
      page,
      size: pageData.value.size,
      name: query.name,
      status: query.status,
    })
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  if (!await ensureModel('导入新项目并生成项目导览')) return
  editingProject.value = null
  modalOpen.value = true
}

function openEdit(project: Project) {
  editingProject.value = project
  modalOpen.value = true
}

async function saveProject(form: ProjectForm) {
  saving.value = true
  try {
    let createdProject: Project | null = null
    if (editingProject.value) {
      const updatedProject = await projectApi.update(editingProject.value.id, form)
      if (selectedProject.value?.id === updatedProject.id) selectedProject.value = updatedProject
      showSuccess('项目修改成功')
    } else {
      createdProject = await projectApi.create(form)
    }
    modalOpen.value = false
    await loadProjects(editingProject.value ? pageData.value.page : 1)
    if (createdProject) await understandProject(createdProject)
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function removeProject(project: Project) {
  if (!window.confirm(`确定删除项目“${project.name}”吗？删除后将不会出现在列表中。`)) return
  deletingId.value = project.id
  try {
    await projectApi.delete(project.id)
    showSuccess('项目已删除')
    const targetPage = pageData.value.items.length === 1 && pageData.value.page > 1
      ? pageData.value.page - 1
      : pageData.value.page
    await loadProjects(targetPage)
    if (selectedProject.value?.id === project.id) selectedProject.value = null
  } catch (error) {
    showError(error)
  } finally {
    deletingId.value = null
  }
}

async function rebuildSource(project: Project) {
  const confirmed = window.confirm(
    `确认重建“${project.name}”当前提交的源码结构？若结构已被 Diff、向量或评测引用，系统会拒绝操作。`,
  )
  if (!confirmed) return
  importingId.value = project.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const task = await projectApi.rebuildSource(project.id)
    showSuccess(`结构重建完成：${task.processedFiles} 个文件，版本 ${task.structureVersion}`)
    await loadProjects(pageData.value.page)
    selectedProject.value = pageData.value.items.find((item) => item.id === project.id) ?? selectedProject.value
  } catch (error) {
    showError(error)
    await loadProjects(pageData.value.page)
  } finally {
    importingId.value = null
  }
}

async function understandProject(project: Project) {
  if (workflowId.value) return
  workflowId.value = project.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await runProjectUnderstanding(project.id, projectApi, (stage) => {
      importStage.value = {
        SOURCE_IMPORT: '第 1/2 步：读取仓库并解析源码，请保持页面打开。',
        EMBEDDING: '第 2/2 步：源码已解析，正在建立检索索引。',
        COMPLETED: '源码与检索索引已准备好。',
      }[stage]
    })
    showSuccess(
      `项目解析完成：识别 ${result.sourceImport.processedFiles} 个文件，建立 ${result.embeddingIndex.processedChunks} 个新检索索引`,
    )
    await loadProjects(pageData.value.page)
    selectedProject.value = pageData.value.items.find((item) => item.id === project.id) ?? {
      ...project,
      status: 'READY',
      currentRevision: result.sourceImport.revision,
      currentStructureVersion: result.sourceImport.structureVersion,
      lastIndexedAt: result.sourceImport.finishedAt,
    }
  } catch (error) {
    showError(error)
    if (importStage.value.startsWith('第 2/2')) {
      errorMessage.value = `源码已解析，但检索索引未完成：${errorMessage.value}。可重新解析继续建立索引，已成功的同版本源码会复用。`
    }
    await loadProjects(pageData.value.page)
  } finally {
    workflowId.value = null
    importStage.value = ''
  }
}

async function runReviewWorkflow(project: Project) {
  if (reviewingId.value) return
  reviewingId.value = project.id
  try {
    if (!await ensureModel()) return
    errorMessage.value = ''
    successMessage.value = ''
    startReviewStatusPolling(project, true)
    const result = await projectApi.createReviewWorkflow(project.id)
    if (selectedProject.value?.id !== project.id) return
    reviewWorkflow.value = result
    if (result.status !== 'RUNNING') stopReviewStatusPolling()
    if (result.status === 'SUCCEEDED') {
      showSuccess('完整代码审查已完成，正在打开审查报告')
      aiReviewProject.value = project
    } else {
      showError(new ApiError(
        `${result.errorMessage || '代码审查未完成'}；${result.recoveryAction || '请检查任务状态后重试'}`,
      ))
    }
    await loadProjects(pageData.value.page)
  } catch (error) {
    showError(error)
    await loadLatestReviewWorkflow(project)
  } finally {
    reviewingId.value = null
  }
}

async function ensureModel(purpose = '使用 AI 问答或审查') {
  if (checkingModel) return checkingModel
  const accountId = authSession.value?.user.id
  checkingModel = checkModel(purpose, accountId)
  try { return await checkingModel } finally { checkingModel = undefined }
}
async function checkModel(purpose: string, accountId?: string) {
  try {
    const providers = await modelConnectionApi.list()
    if (!accountId || authSession.value?.user.id !== accountId) return false
    if (providers.some((item) => item.active && item.configured)) return true
    modelSetupPurpose.value = purpose
    modelSetupOpen.value = true
  } catch (error) {
    showError(error)
  }
  return false
}

async function openConversation(project: Project, initialQuestion = '') {
  // Reading saved conversations does not require a configured or currently reachable model.
  if (selectedProject.value?.id !== project.id) return
  conversationInitialQuestion.value = initialQuestion
  conversationProject.value = project
}

async function chatFromSource(question: string) {
  const project = sourceProject.value
  if (!project || selectedProject.value?.id !== project.id) return
  await openConversation(project, question)
  if (sourceProject.value?.id === project.id) sourceProject.value = null
}

async function loadLatestReviewWorkflow(project: Project) {
  stopReviewStatusPolling()
  reviewWorkflow.value = undefined
  const latest = await fetchLatestReviewWorkflow(project, true)
  if (latest?.status === 'RUNNING') startReviewStatusPolling(project)
}

function stopReviewStatusPolling() {
  if (reviewStatusTimer !== undefined) {
    window.clearInterval(reviewStatusTimer)
    reviewStatusTimer = undefined
  }
}

function startReviewStatusPolling(project: Project, fetchImmediately = false) {
  stopReviewStatusPolling()
  const poll = async () => {
    if (selectedProject.value?.id !== project.id) {
      stopReviewStatusPolling()
      return
    }
    await fetchLatestReviewWorkflow(project, true)
  }
  reviewStatusTimer = window.setInterval(poll, 3000)
  if (fetchImmediately) void poll()
}

async function refreshReviewWorkflow(project: Project) {
  const latest = await fetchLatestReviewWorkflow(project, false)
  if (latest?.status === 'RUNNING') startReviewStatusPolling(project)
}

async function fetchLatestReviewWorkflow(project: Project, tolerateMissing: boolean) {
  try {
    const latest = await projectApi.latestReviewWorkflow(project.id)
    if (selectedProject.value?.id !== project.id) return
    reviewWorkflow.value = latest
    if (latest.status !== 'RUNNING') stopReviewStatusPolling()
    return latest
  } catch (error) {
    if (selectedProject.value?.id !== project.id) return
    if (error instanceof ApiError && error.status === 404) {
      if (!tolerateMissing) reviewWorkflow.value = undefined
      return
    }
    stopReviewStatusPolling()
    showError(error)
  }
}

function openWorkspace(project: Project) {
  workspaceArea.value = 'projects'
  selectedProject.value = project
  void loadLatestReviewWorkflow(project)
}

function chatFromHome(project: Project) {
  openWorkspace(project)
  void openConversation(project)
}

function openProjectArea() {
  const returningFromProject = selectedProject.value !== null
  stopReviewStatusPolling()
  workspaceArea.value = 'projects'
  selectedProject.value = null
  if (returningFromProject) void loadProjects(pageData.value.page)
}

function openGenerationArea() {
  stopReviewStatusPolling()
  workspaceArea.value = 'generation'
  selectedProject.value = null
}

function openRetrieval(project: Project, queryText = '') {
  retrievalInitialQuery.value = queryText
  retrievalProject.value = project
}

function resetFilters() {
  query.name = ''
  query.status = ''
  void loadProjects(1)
}

function formatDate(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function handleAuthenticated(session: AuthSession) {
  setAuthSession(session)
  authSession.value = session
  void loadProjects(1)
}

function logout() {
  modelSetupOpen.value = false
  modelConnectionsOpen.value = false
  stopReviewStatusPolling()
  clearAuthSession()
  authSession.value = null
  pageData.value = { page: 1, size: 10, total: 0, pages: 0, items: [] }
  selectedProject.value = null
  workspaceArea.value = 'projects'
}

onMounted(() => {
  unsubscribeAuth = subscribeAuthSession(() => {
    authSession.value = getAuthSession()
  })
  if (authSession.value) void loadProjects()
})
onUnmounted(() => {
  stopReviewStatusPolling()
  unsubscribeAuth?.()
})
</script>

<template>
  <AuthView v-if="!authSession" @authenticated="handleAuthenticated" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="DevMate AI 首页" @click.prevent="openProjectArea">
        <span class="brand-mark" aria-hidden="true">🌱</span>
        <span><b>DevMate</b><small>你的项目探索搭子</small></span>
      </a>
      <nav>
        <button class="nav-item" :class="{ active: workspaceArea === 'projects' && !selectedProject }" type="button" @click="openProjectArea"><span aria-hidden="true">🗂️</span>我的项目</button>
        <button v-if="selectedProject" class="nav-item active" type="button"><span aria-hidden="true">🧩</span>理解工作台</button>
        <button class="nav-item" type="button" @click="modelConnectionsOpen = true"><span aria-hidden="true">🔌</span>大模型连接</button>
        <details class="experimental-nav"><summary>实验功能</summary>
          <p>项目生成目前仅完成需求澄清，暂不生成工程。</p>
          <button class="nav-item" type="button" @click="openGenerationArea">生成项目（需求草案）</button>
        </details>
      </nav>
      <div class="sidebar-note"><span aria-hidden="true">🌷</span><b>陌生的代码，也能慢慢读懂</b><p>先看项目做什么，再探索它是怎么做到的。</p></div>
      <div class="sidebar-footer">
        <span class="connection-dot"></span>
        <div class="signed-user"><b>{{ authSession.user.username }}</b><small>{{ authSession.user.email || '已认证用户' }}</small></div>
        <button class="logout-button" type="button" title="退出登录" aria-label="退出登录" @click="logout">↪</button>
      </div>
    </aside>

    <main>
      <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
      <div v-if="successMessage" class="notice success" role="status">{{ successMessage }}</div>
      <div v-if="workflowId" class="notice" role="status">{{ importStage }} 大型仓库可能需要更长时间；请勿重复提交。</div>
      <GenerationWorkspace v-if="workspaceArea === 'generation'" />
      <ProjectUnderstandingWorkspace
        v-else-if="selectedProject"
        :project="selectedProject"
        :parsing="workflowId === selectedProject.id"
        :rebuilding="importingId === selectedProject.id"
        :deleting="deletingId === selectedProject.id"
        :reviewing="reviewingId === selectedProject.id"
        :review-workflow="reviewWorkflow?.projectId === selectedProject.id ? reviewWorkflow : undefined"
        @back="openProjectArea()"
        @configure-model="modelConnectionsOpen = true"
        @structure="sourceProject = selectedProject"
        @chat="openConversation(selectedProject, $event)"
        @search="openRetrieval(selectedProject, $event)"
        @reparse="understandProject(selectedProject)"
        @rebuild="rebuildSource(selectedProject)"
        @diff="diffProject = selectedProject"
        @static-analysis="analysisProject = selectedProject"
        @review="aiReviewProject = selectedProject"
        @run-review="runReviewWorkflow(selectedProject)"
        @refresh-review="refreshReviewWorkflow(selectedProject)"
        @evaluation="evaluationProject = selectedProject"
        @edit="openEdit(selectedProject)"
        @delete="removeProject(selectedProject)"
      />
      <section v-else class="home-page" aria-label="项目首页">
      <header class="home-topline"><p>你的项目小天地 <span aria-hidden="true">☁️</span></p><span>从好奇开始，一点点看懂代码</span></header>
      <section class="home-welcome" aria-labelledby="home-title">
        <div class="home-welcome-copy">
          <p class="home-greeting">👋 欢迎回来，{{ authSession.user.username }}</p>
          <h1 id="home-title">今天，想读懂哪个项目？</h1>
          <p class="home-intro">把陌生仓库变成看得懂的故事。<br />从项目介绍到业务地图，再和 AI 一起深入代码。</p>
          <div class="home-welcome-actions"><button class="button primary" type="button" @click="openCreate">＋ 导入新项目</button><button class="home-model-button" type="button" @click="modelConnectionsOpen = true">🔌 连接我的 AI <span aria-hidden="true">↗</span></button></div>
        </div>
        <div class="home-illustration" aria-hidden="true"><span class="home-spark one">✦</span><span class="home-floating-note">💬 原来是这样！</span><div class="home-book"><div class="home-book-cover"><span>🌱</span><b>PROJECT<br />STORIES</b><span class="home-book-face">• ◡ •</span></div></div><span class="home-little-flower">🌷</span><span class="home-spark two">✧</span><span class="home-illustration-caption">每个项目，都有自己的小故事</span></div>
      </section>

      <section class="home-path" aria-label="使用步骤">
        <div><span aria-hidden="true">📦</span><p><b>带来一个项目</b><small>粘贴 Git 仓库地址</small></p></div><span class="home-path-arrow" aria-hidden="true">→</span>
        <div><span aria-hidden="true">🗺️</span><p><b>看看它的故事</b><small>先读 README，再核对源码</small></p></div><span class="home-path-arrow" aria-hidden="true">→</span>
        <div><span aria-hidden="true">💬</span><p><b>和 AI 一起探索</b><small>带着代码证据继续追问</small></p></div>
      </section>

      <section class="home-projects panel" aria-labelledby="home-projects-title">
        <header class="home-projects-heading"><div><span aria-hidden="true">🪴</span><div><h2 id="home-projects-title">我的项目收藏</h2><p>找到一个感兴趣的项目，接着探索吧。</p></div></div><span class="home-project-count">{{ loading ? '正在整理…' : `当前条件共 ${pageData.total} 个 · 本页 ${pageData.items.filter(item => item.status === 'READY').length} 个可阅读` }}</span></header>
        <div class="toolbar">
          <form class="filters" @submit.prevent="loadProjects(1)">
            <label class="search-field">
              <span>⌕</span>
              <input v-model="query.name" placeholder="搜索项目名称" maxlength="100" />
            </label>
            <select v-model="query.status" aria-label="项目状态">
              <option value="">全部状态</option>
              <option value="CREATED">待索引</option>
              <option value="INDEXING">索引中</option>
              <option value="READY">已就绪</option>
              <option value="FAILED">失败</option>
            </select>
            <button class="button secondary" type="submit">筛选</button>
            <button v-if="query.name || query.status" class="text-button" type="button" @click="resetFilters">重置</button>
          </form>
          <button class="icon-button" type="button" aria-label="刷新" :disabled="loading" @click="loadProjects(pageData.page)">↻</button>
        </div>


        <div v-if="loading" class="state-box home-loading" role="status"><span class="loader"></span><p>正在整理你的项目书架…</p></div>
        <div v-else-if="!hasProjects" class="state-box empty home-empty">
          <div class="empty-icon" aria-hidden="true">{{ query.name || query.status ? '🔎' : '🌱' }}</div>
          <h2>{{ query.name || query.status ? '这次没有找到匹配的项目' : '给你的项目书架种下第一颗种子' }}</h2>
          <p>{{ query.name || query.status ? '换个关键词，或者清除筛选再看看。' : '带来一个 Java、Python、JavaScript 或其他支持的文本仓库，我们一起读懂它。' }}</p>
          <button v-if="query.name || query.status" class="button secondary" type="button" @click="resetFilters">清除筛选</button>
          <button v-else class="button primary" type="button" @click="openCreate">＋ 导入第一个项目</button>
        </div>

        <div v-else class="home-project-grid">
          <article v-for="(project, index) in pageData.items" :key="project.id" class="home-project-card" :data-tone="index % 4">
            <header class="home-card-header"><span class="home-project-avatar" aria-hidden="true">{{ projectEmoji(project) }}</span><span class="status" :class="project.status.toLowerCase()"><i></i>{{ statusLabel[project.status] }}</span></header>
            <h3>{{ project.name }}</h3>
            <p class="home-project-description">{{ project.description || '这个项目的故事等你来探索，打开导览看看吧。' }}</p>
            <div class="home-project-meta"><span>🗃️ {{ project.sourceType === 'GIT' ? 'Git 仓库' : project.sourceType }}</span><span>🌿 {{ project.defaultBranch || '仓库默认分支' }}</span></div>
            <p class="home-project-updated">更新于 {{ formatDate(project.updatedAt) }}</p>
            <footer class="row-actions">
              <button v-if="project.status === 'READY'" class="open-workspace" type="button" :disabled="workflowId === project.id || importingId === project.id || deletingId === project.id" @click="openWorkspace(project)">打开理解工作台 <span>→</span></button>
              <button v-else class="parse-project" type="button" :disabled="project.sourceType !== 'GIT' || project.status === 'INDEXING' || workflowId === project.id || importingId === project.id || deletingId === project.id" @click="understandProject(project)">{{ workflowId === project.id || project.status === 'INDEXING' ? '正在解析…' : project.status === 'FAILED' ? '重新解析' : '开始解析' }}</button>
              <button v-if="project.status === 'READY'" class="home-project-chat" type="button" :disabled="workflowId === project.id || importingId === project.id || deletingId === project.id" :aria-label="`与 AI 聊聊${project.name}`" title="与 AI 聊聊项目" @click="chatFromHome(project)">💬</button>
              <button class="project-edit" type="button" :disabled="workflowId === project.id || importingId === project.id || deletingId === project.id" aria-label="编辑项目" title="编辑项目" @click="openEdit(project)">•••</button>
            </footer>
          </article>
        </div>

        <footer v-if="hasProjects" class="pagination">
          <span>{{ rangeText }}</span>
          <div>
            <button :disabled="pageData.page <= 1 || loading" @click="loadProjects(pageData.page - 1)">上一页</button>
            <button :disabled="pageData.page >= pageData.pages || loading" @click="loadProjects(pageData.page + 1)">下一页</button>
          </div>
        </footer>
      </section>
      <p class="home-footer-note">☕ 慢慢来，理解比翻完每一行更重要。<span>Java 项目还支持代码变更审查。</span></p>
      </section>
    </main>

    <ProjectFormModal
      :open="modalOpen"
      :project="editingProject"
      :saving="saving"
      @close="modalOpen = false"
      @submit="saveProject"
    />
    <SourceStructureModal
      :key="sourceProject?.id || 'closed-source'"
      :open="sourceProject !== null"
      :project-id="sourceProject?.id"
      :project-name="sourceProject?.name"
      @configure-model="modelConnectionsOpen = true"
      @chat="chatFromSource"
      @close="sourceProject = null"
    />
    <DiffReportModal
      :open="diffProject !== null"
      :project-id="diffProject?.id"
      :project-name="diffProject?.name"
      @close="diffProject = null"
    />
    <StaticAnalysisModal
      :open="analysisProject !== null"
      :project-id="analysisProject?.id"
      :project-name="analysisProject?.name"
      @close="analysisProject = null"
    />
    <RetrievalModal
      :open="retrievalProject !== null"
      :project-id="retrievalProject?.id"
      :project-name="retrievalProject?.name"
      :initial-query="retrievalInitialQuery"
      @close="retrievalProject = null; retrievalInitialQuery = ''"
    />
    <AiReviewModal
      :open="aiReviewProject !== null"
      :project-id="aiReviewProject?.id"
      :project-name="aiReviewProject?.name"
      @close="aiReviewProject = null"
    />
    <ReviewEvaluationModal
      :open="evaluationProject !== null"
      :project-id="evaluationProject?.id"
      :project-name="evaluationProject?.name"
      @close="evaluationProject = null"
    />
    <ModelSetupRequiredDialog :open="modelSetupOpen" :purpose="modelSetupPurpose" @close="modelSetupOpen = false" @configure="modelSetupOpen = false; modelConnectionsOpen = true" />
    <ModelConnectionModal :open="modelConnectionsOpen" @close="modelConnectionsOpen = false" />
    <AgentConversationModal
      :key="conversationProject?.id || 'closed-chat'"
      :open="conversationProject !== null"
      :project-id="conversationProject?.id"
      :project-name="conversationProject?.name"
      :initial-question="conversationInitialQuestion"
      @configure-model="conversationProject = null; modelConnectionsOpen = true"
      @close="conversationProject = null"
    />
  </div>
</template>

<style scoped>
.app-shell { background: #faf9f5; color: #343344; }
.sidebar { background: #f2eef7; color: #4a405b; border-right: 1px solid #e5deed; padding: 28px 18px 24px; }
.brand { padding-inline: 8px; }
.brand-mark { width: 46px; height: 46px; border: 1px solid #e3d7eb; border-radius: 17px; font-size: 28px; background: #fffaf7; box-shadow: 0 3px 0 #e3dbea; transform: rotate(-5deg); }
.brand b { color: #49375f; font-size: 20px; }
.brand small { color: #85738e; letter-spacing: .06em; font-size: 11px; line-height: 1.5; font-family: inherit; }
.sidebar nav { gap: 9px; margin-top: 38px; }
.sidebar .nav-item { color: #776984; min-height: 48px; border-radius: 14px; font-size: 13px; }
.sidebar .nav-item span { font-size: 20px; }
.sidebar .nav-item.active { background: #fffdfa; color: #654f7f; box-shadow: 0 3px 0 #e4dbea; }
.sidebar .nav-item:hover { background: #fffcf8; }
.experimental-nav { color: #81748a; margin-top: 16px; }
.sidebar-note { margin: auto 8px 28px; padding-top: 38px; }
.sidebar-note > span { display: block; font-size: 38px; margin-bottom: 12px; }
.sidebar-note b { font-size: 12px; font-weight: 600; }
.sidebar-note p { font-size: 11px; line-height: 1.8; color: #8a7d93; margin: 8px 0 0; }
.sidebar-footer { margin-top: 0; padding: 18px 6px 0; border-top-color: #e0d6e8; }
.sidebar-footer small { color: #887b91; }
.connection-dot { background: #80a88f; box-shadow: 0 0 0 4px #e2e9e1; }
.logout-button { color: #80708e; border-color: #d9cfe4; border-radius: 10px; }
.app-shell > main { min-width: 0; padding-top: 28px; }
.home-page { max-width: 1280px; margin-inline: auto; }
.home-topline { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin: 0 0 24px; }
.home-topline p { font-size: 13px; font-weight: 700; color: #6b5d79; margin: 0; }
.home-topline > span { color: #8b8390; font-size: 11px; }
.home-welcome { display: grid; grid-template-columns: minmax(0, 1fr) 280px; align-items: center; gap: 24px; border: 1px solid #e5dcf0; padding: 36px 40px; border-radius: 28px; background: linear-gradient(112deg, #f1eafb, #f8f0ed 62%, #f5f4df); overflow: hidden; }
.home-greeting { color: #7b658a; font-size: 13px; margin: 0 0 16px; overflow-wrap: anywhere; }
.home-welcome h1 { color: #4c3b60; font-size: clamp(25px, 2.8vw, 38px); letter-spacing: -.025em; line-height: 1.4; margin: 0 0 12px; }
.home-intro { color: #7a6b80; font-size: 13px; line-height: 1.9; margin-bottom: 24px; }
.home-welcome-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.home-page .button { border-radius: 13px; min-height: 42px; font-size: 12px; }
.home-page .button.primary { color: #fff; background: #775b99; box-shadow: 0 4px 0 #5f447e; border: 1px solid #6c508b; }
.home-model-button { border: 0; background: #ffffffa6; color: #766081; padding: 12px 15px; border-radius: 13px; font-size: 12px; }
.home-illustration { position: relative; height: 225px; user-select: none; }
.home-book { position: absolute; top: 24px; left: 58px; width: 143px; height: 164px; background: #d3bfdc; border: 2px solid #a78cbb; border-radius: 8px 22px 22px 8px; padding: 0 0 7px; transform: rotate(-10deg); box-shadow: 12px 15px 0 #dcd1cb66; }
.home-book-cover { position: relative; height: 100%; border: 1px solid #b09bc4; border-radius: 6px 19px 19px 6px; border-left: 8px solid #c0a3d4; background: #f8eefc; padding: 17px 20px; }
.home-book-cover > span:first-child { font-size: 30px; }
.home-book-cover b { display: block; color: #9272a7; font-size: 12px; letter-spacing: .1em; line-height: 1.6; margin-top: 4px; }
.home-book-face { position: absolute; left: 51px; bottom: 12px; font-size: 19px; color: #6a4d80; white-space: nowrap; }
.home-floating-note { position: absolute; top: 3px; right: 0; z-index: 1; padding: 10px 13px; border-radius: 15px 15px 15px 3px; border: 1px solid #e6dcd4; background: #fffbf3; color: #887261; font-size: 11px; transform: rotate(7deg); }
.home-little-flower { position: absolute; bottom: 27px; right: 3px; font-size: 53px; transform: rotate(8deg); }
.home-spark { position: absolute; color: #b197c4; font-size: 24px; }
.home-spark.one { top: 2px; left: 22px; }
.home-spark.two { right: 2px; top: 78px; color: #b2ab72; }
.home-illustration-caption { position: absolute; bottom: 0; left: 31px; color: #9b8b8b; font-size: 10px; }
.home-path { display: flex; align-items: center; justify-content: space-around; gap: 12px; padding: 24px 10px; }
.home-path > div { display: flex; align-items: center; gap: 12px; }
.home-path > div > span { font-size: 24px; }
.home-path p { display: grid; gap: 5px; margin: 0; }
.home-path b { color: #62596b; font-size: 12px; }
.home-path small { color: #8a8290; font-size: 10px; }
.home-path-arrow { color: #b9adbf; }
.home-projects { border: 1px solid #e9e3e9; background: #fffefa; border-radius: 24px; box-shadow: none; }
.home-projects-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; padding: 24px 24px 0; }
.home-projects-heading > div { display: flex; align-items: center; gap: 12px; }
.home-projects-heading > div > span { font-size: 28px; }
.home-projects-heading h2 { font-size: 19px; color: #51465c; margin: 0 0 4px; }
.home-projects-heading p { margin: 0; color: #8a8190; font-size: 11px; }
.home-project-count { font-size: 10px; color: #7b6f86; background: #f4eff8; border-radius: 10px; padding: 7px 10px; }
.home-projects .toolbar { border: 0; padding: 20px 24px; }
.home-projects .filters { flex-wrap: wrap; }
.home-projects .search-field { border-radius: 12px; background: #faf8fc; border-color: #e5deeb; max-width: 320px; min-width: min(100%, 190px); }
.home-projects select { font-size: 12px; background: #fffdfa; border-color: #e5deeb; border-radius: 12px; }
.home-projects .button.secondary { color: #716080; border-color: #e5deeb; }
.home-project-grid { padding: 0 24px 24px; display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 18px; }
.home-project-card { min-width: 0; border: 1px solid #dddfe9; border-radius: 20px; padding: 20px; background: #faf9fd; display: flex; flex-direction: column; box-shadow: 0 3px 0 #e8e5ef; transition: transform .16s ease, box-shadow .16s ease; }
.home-project-card[data-tone="1"] { background: #f5faf6; border-color: #dce7dd; box-shadow: 0 3px 0 #e0ebe1; }
.home-project-card[data-tone="2"] { background: #fff9f2; border-color: #ede1cf; box-shadow: 0 3px 0 #eee5d7; }
.home-project-card[data-tone="3"] { background: #f5f9fe; border-color: #dce4f0; box-shadow: 0 3px 0 #e1e8f1; }
.home-project-card:hover { transform: translateY(-2px); box-shadow: 0 5px 0 #e4deeb; }
.home-card-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 16px; }
.home-project-avatar { display: grid; place-items: center; width: 48px; height: 48px; background: #ffffffc9; border: 1px solid #e6e0ec; border-radius: 16px; font-size: 27px; }
.home-project-card .status { padding: 5px 8px; border: 1px solid #e8e0ce; background: #fff9e9; color: #816c38; border-radius: 20px; font-size: 10px; }
.home-project-card .status.ready { background: #eef7ee; border-color: #d7e6d7; color: #58785e; }
.home-project-card .status.indexing { background: #edf4fc; border-color: #d8e4f3; color: #5d749a; }
.home-project-card .status.failed { background: #fff0ee; border-color: #f3d9d5; color: #a35c56; }
.home-project-card h3 { margin: 0 0 9px; font-size: 16px; line-height: 1.5; color: #52475e; overflow-wrap: anywhere; }
.home-project-description { color: #817988; font-size: 11px; line-height: 1.8; margin-bottom: 18px; overflow-wrap: anywhere; }
.home-project-meta { margin-top: auto; display: flex; flex-wrap: wrap; gap: 7px; }
.home-project-meta span { font-size: 10px; color: #81748a; background: #ffffffb3; padding: 5px 8px; border-radius: 8px; overflow-wrap: anywhere; min-width: 0; }
.home-project-updated { color: #8e8494; font-size: 10px; margin: 12px 0 16px; }
.home-project-card .row-actions { display: flex; gap: 6px; align-items: center; border-top: 1px solid #e6e0e9; padding-top: 14px; white-space: normal; }
.home-project-card .row-actions .open-workspace,.home-project-card .row-actions .parse-project { margin-right: auto; border-radius: 10px; padding: 9px 12px; background: #ebe4f5; color: #6c5088; font-size: 11px; }
.home-project-card .row-actions .open-workspace span { color: #8969a6; }
.home-project-card .row-actions .parse-project { background: #f1e8d6; color: #857044; }
.home-project-card .row-actions .home-project-chat,.home-project-card .row-actions .project-edit { flex: 0 0 32px; height: 32px; width: 32px; padding: 0; margin: 0; border: 1px solid #e6dfeb; border-radius: 10px; background: #fffcf9; color: #8c7b9c; }
.home-projects .pagination { padding: 16px 24px; border-color: #eee6ed; }
.home-projects .pagination button { border-radius: 10px; color: #7b6a8b; border-color: #e6dfeb; }
.home-empty { padding: 36px 20px; }
.home-empty .empty-icon { width: 68px; height: 68px; font-size: 36px; border-radius: 24px; background: #f3edf7; color: #7d668f; }
.home-empty h2 { color: #6d5c7b; }
.home-empty p { max-width: 420px; line-height: 1.8; color: #8a7f92; }
.home-footer-note { font-size: 11px; color: #9a8e9f; text-align: center; margin: 24px 0 0; line-height: 1.8; }
.home-footer-note span { display: inline-block; margin-left: 12px; }
.home-page button:focus-visible,.sidebar a:focus-visible,.sidebar button:focus-visible,.home-page select:focus-visible { outline: 3px solid #aa8fc5; outline-offset: 3px; }
@media(max-width:1150px) { .home-welcome { grid-template-columns: minmax(0, 1fr) 210px; padding: 28px; } .home-illustration { transform: scale(.85); transform-origin: center; } .home-book { left: 22px; } .home-floating-note { right: -12px; } .home-illustration-caption { left: 0; } }
@media(max-width:850px) { .sidebar { padding: 16px 20px; } .sidebar nav { margin-top: 16px; } .sidebar-note { display: none; } .home-path { justify-content: space-between; } }
@media(max-width:650px) { .home-welcome { grid-template-columns: minmax(0, 1fr); padding: 24px; } .home-illustration { display: none; } .home-topline > span { display: none; } .home-path { flex-direction: column; align-items: flex-start; padding: 24px 14px; gap: 18px; } .home-path-arrow { display: none; } .home-projects-heading { padding: 20px 18px 0; } .home-projects .toolbar { padding: 18px; } .home-project-grid { padding: 0 18px 22px; } .home-projects .filters { gap: 8px; } .home-projects .filters select { flex: 1; } .home-footer-note span { display: block; margin-left: 0; } }
@media(prefers-reduced-motion:reduce) { .home-project-card { transition: none; } .home-project-card:hover { transform: none; } .home-page .button { transition: none; } .home-page .button:hover:not(:disabled) { transform: none; } }
</style>
