<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
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
const reviewingId = ref<string | null>(null)
const reviewWorkflow = ref<ReviewWorkflow | undefined>()
const authSession = ref<AuthSession | null>(getAuthSession())
const workspaceArea = ref<'projects' | 'generation'>('projects')
let unsubscribeAuth: (() => void) | undefined

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
  errorMessage.value = ''
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

function openCreate() {
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
  workflowId.value = project.id
  try {
    const result = await runProjectUnderstanding(project.id)
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
    await loadProjects(pageData.value.page)
  } finally {
    workflowId.value = null
  }
}

async function runReviewWorkflow(project: Project) {
  reviewingId.value = project.id
  reviewWorkflow.value = undefined
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await projectApi.createReviewWorkflow(project.id)
    reviewWorkflow.value = result
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
  } finally {
    reviewingId.value = null
  }
}

async function loadLatestReviewWorkflow(project: Project) {
  reviewWorkflow.value = undefined
  try {
    reviewWorkflow.value = await projectApi.latestReviewWorkflow(project.id)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 404) showError(error)
  }
}

function openWorkspace(project: Project) {
  workspaceArea.value = 'projects'
  selectedProject.value = project
  void loadLatestReviewWorkflow(project)
}

function openProjectArea() {
  workspaceArea.value = 'projects'
  selectedProject.value = null
}

function openGenerationArea() {
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
onUnmounted(() => unsubscribeAuth?.())
</script>

<template>
  <AuthView v-if="!authSession" @authenticated="handleAuthenticated" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="DevMate AI 首页">
        <span class="brand-mark">D</span>
        <span><b>DevMate</b><small>JAVA REVIEW AGENT</small></span>
      </a>
      <nav>
        <button class="nav-item" :class="{ active: workspaceArea === 'projects' && !selectedProject }" type="button" @click="openProjectArea"><span>⌘</span>代码审查项目</button>
        <button v-if="selectedProject" class="nav-item active" type="button"><span>◎</span>审查工作台</button>
        <button class="nav-item" :class="{ active: workspaceArea === 'generation' }" type="button" @click="openGenerationArea"><span>＋</span>生成项目（实验）</button>
      </nav>
      <div class="sidebar-footer">
        <span class="connection-dot"></span>
        <div class="signed-user"><b>{{ authSession.user.username }}</b><small>{{ authSession.user.email || '已认证用户' }}</small></div>
        <button class="logout-button" type="button" title="退出登录" aria-label="退出登录" @click="logout">↪</button>
      </div>
    </aside>

    <main>
      <GenerationWorkspace v-if="workspaceArea === 'generation'" />
      <ProjectUnderstandingWorkspace
        v-else-if="selectedProject"
        :project="selectedProject"
        :parsing="workflowId === selectedProject.id"
        :rebuilding="importingId === selectedProject.id"
        :deleting="deletingId === selectedProject.id"
        :reviewing="reviewingId === selectedProject.id"
        :review-workflow="reviewWorkflow?.projectId === selectedProject.id ? reviewWorkflow : undefined"
        @back="selectedProject = null"
        @structure="sourceProject = selectedProject"
        @search="openRetrieval(selectedProject, $event)"
        @reparse="understandProject(selectedProject)"
        @rebuild="rebuildSource(selectedProject)"
        @diff="diffProject = selectedProject"
        @static-analysis="analysisProject = selectedProject"
        @review="aiReviewProject = selectedProject"
        @run-review="runReviewWorkflow(selectedProject)"
        @evaluation="evaluationProject = selectedProject"
        @edit="openEdit(selectedProject)"
        @delete="removeProject(selectedProject)"
      />
      <template v-else>
      <header class="topbar">
        <div>
          <p class="eyebrow">WORKSPACE / PROJECTS</p>
          <h1>审查 Java 代码变更</h1>
          <p>导入 Java 仓库，通过 Diff、静态分析、RAG 和 Agent 发现有证据的工程风险。</p>
        </div>
        <button class="button primary" type="button" @click="openCreate"><span>＋</span> 导入项目</button>
      </header>

      <section class="summary-grid" aria-label="项目概览">
        <article><small>已导入项目</small><strong>{{ pageData.total }}</strong><span>可以随时继续理解和开发</span></article>
        <article><small>解析就绪</small><strong>{{ pageData.items.filter((item) => item.status === 'READY').length }}</strong><span>当前页可进入理解工作台</span></article>
        <article class="accent-card"><small>核心能力</small><strong>RAG 审查</strong><span>变更定位、项目上下文和受控 Agent</span></article>
      </section>

      <section class="panel">
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

        <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
        <div v-if="successMessage" class="notice success" role="status">{{ successMessage }}</div>

        <div v-if="loading" class="state-box"><span class="loader"></span><p>正在加载项目…</p></div>
        <div v-else-if="!hasProjects" class="state-box empty">
          <div class="empty-icon">⌘</div>
          <h2>还没有符合条件的项目</h2>
          <p>导入一个 Java Git 仓库，系统会自动解析并建立可搜索的代码知识。</p>
          <button class="button primary" type="button" @click="openCreate">导入第一个项目</button>
        </div>

        <div v-else class="table-wrap">
          <table>
            <thead><tr><th>项目</th><th>源码</th><th>状态</th><th>更新时间</th><th><span class="sr-only">操作</span></th></tr></thead>
            <tbody>
              <tr v-for="project in pageData.items" :key="project.id">
                <td>
                  <div class="project-cell">
                    <span class="project-icon">{{ project.name.slice(0, 1).toUpperCase() }}</span>
                    <div><b>{{ project.name }}</b><small>{{ project.description || '暂无项目描述' }}</small></div>
                  </div>
                </td>
                <td>
                  <span class="source-badge">{{ project.sourceType }}</span>
                  <small class="branch">{{ project.defaultBranch || '—' }}</small>
                  <small v-if="project.currentStructureVersion" class="branch">{{ project.currentStructureVersion }}</small>
                </td>
                <td><span class="status" :class="project.status.toLowerCase()"><i></i>{{ statusLabel[project.status] }}</span></td>
                <td class="muted">{{ formatDate(project.updatedAt) }}</td>
                <td class="row-actions">
                  <button
                    v-if="project.status === 'READY'"
                    class="open-workspace"
                    type="button"
                    :disabled="workflowId === project.id || importingId === project.id || deletingId === project.id"
                    @click="openWorkspace(project)"
                  >打开理解工作台 <span>→</span></button>
                  <button
                    v-else
                    class="parse-project"
                    type="button"
                    :disabled="project.sourceType !== 'GIT' || workflowId === project.id || importingId === project.id || deletingId === project.id"
                    @click="understandProject(project)"
                  >
                    {{ workflowId === project.id ? '正在解析…' : project.status === 'FAILED' ? '重新解析' : '解析项目' }}
                  </button>
                  <button
                    class="project-edit"
                    type="button"
                    :disabled="workflowId === project.id || importingId === project.id || deletingId === project.id"
                    aria-label="编辑项目"
                    title="编辑项目"
                    @click="openEdit(project)"
                  >•••</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer v-if="hasProjects" class="pagination">
          <span>{{ rangeText }}</span>
          <div>
            <button :disabled="pageData.page <= 1 || loading" @click="loadProjects(pageData.page - 1)">上一页</button>
            <button :disabled="pageData.page >= pageData.pages || loading" @click="loadProjects(pageData.page + 1)">下一页</button>
          </div>
        </footer>
      </section>
      </template>
    </main>

    <ProjectFormModal
      :open="modalOpen"
      :project="editingProject"
      :saving="saving"
      @close="modalOpen = false"
      @submit="saveProject"
    />
    <SourceStructureModal
      :open="sourceProject !== null"
      :project-id="sourceProject?.id"
      :project-name="sourceProject?.name"
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
  </div>
</template>
