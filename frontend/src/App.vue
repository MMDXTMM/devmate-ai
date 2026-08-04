<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ProjectFormModal from './components/ProjectFormModal.vue'
import SourceStructureModal from './components/SourceStructureModal.vue'
import DiffReportModal from './components/DiffReportModal.vue'
import StaticAnalysisModal from './components/StaticAnalysisModal.vue'
import RetrievalModal from './components/RetrievalModal.vue'
import { ApiError, projectApi } from './services/projectApi'
import type { PageData, Project, ProjectForm, ProjectStatus } from './types/project'

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
const sourceProject = ref<Project | null>(null)
const diffProject = ref<Project | null>(null)
const analysisProject = ref<Project | null>(null)
const retrievalProject = ref<Project | null>(null)

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
    if (editingProject.value) {
      await projectApi.update(editingProject.value.id, form)
      showSuccess('项目修改成功')
    } else {
      await projectApi.create(form)
      showSuccess('项目创建成功')
    }
    modalOpen.value = false
    await loadProjects(editingProject.value ? pageData.value.page : 1)
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
  } catch (error) {
    showError(error)
  } finally {
    deletingId.value = null
  }
}

async function importSource(project: Project) {
  if (project.sourceType !== 'GIT') {
    showError(new ApiError('当前版本只支持导入 Git 项目'))
    return
  }
  importingId.value = project.id
  try {
    const task = await projectApi.importSource(project.id)
    showSuccess(`源码导入成功：发现 ${task.totalFiles} 个 Java 文件`)
    await loadProjects(pageData.value.page)
  } catch (error) {
    showError(error)
    await loadProjects(pageData.value.page)
  } finally {
    importingId.value = null
  }
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

onMounted(() => loadProjects())
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="DevMate AI 首页">
        <span class="brand-mark">D</span>
        <span><b>DevMate</b><small>AI CODE REVIEW</small></span>
      </a>
      <nav>
        <a class="nav-item active" href="#"><span>⌘</span>项目空间</a>
        <a class="nav-item disabled" href="#"><span>◎</span>审查任务<em>即将开放</em></a>
        <a class="nav-item disabled" href="#"><span>◇</span>知识库</a>
      </nav>
      <div class="sidebar-footer">
        <span class="connection-dot"></span>
        <div><b>API 已配置</b><small>localhost:8080</small></div>
      </div>
    </aside>

    <main>
      <header class="topbar">
        <div>
          <p class="eyebrow">WORKSPACE / PROJECTS</p>
          <h1>项目空间</h1>
          <p>管理需要建立代码知识库和执行智能审查的 Java 项目。</p>
        </div>
        <button class="button primary" type="button" @click="openCreate"><span>＋</span> 新建项目</button>
      </header>

      <section class="summary-grid" aria-label="项目概览">
        <article><small>项目总数</small><strong>{{ pageData.total }}</strong><span>已接入的代码仓库</span></article>
        <article><small>当前页</small><strong>{{ pageData.page }}<i>/{{ Math.max(pageData.pages, 1) }}</i></strong><span>{{ rangeText }}</span></article>
        <article class="accent-card"><small>当前能力</small><strong>Context Retrieval</strong><span>Diff、调用关系与预算裁剪</span></article>
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
          <p>创建一个项目，开始构建代码知识库。</p>
          <button class="button primary" type="button" @click="openCreate">新建项目</button>
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
                <td><span class="source-badge">{{ project.sourceType }}</span><small class="branch">{{ project.defaultBranch || '—' }}</small></td>
                <td><span class="status" :class="project.status.toLowerCase()"><i></i>{{ statusLabel[project.status] }}</span></td>
                <td class="muted">{{ formatDate(project.updatedAt) }}</td>
                <td class="row-actions">
                  <button
                    class="import"
                    type="button"
                    :disabled="project.sourceType !== 'GIT' || importingId === project.id || deletingId === project.id"
                    @click="importSource(project)"
                  >
                    {{ importingId === project.id ? '导入中' : project.status === 'READY' ? '重新导入' : '导入源码' }}
                  </button>
                  <button
                    type="button"
                    :disabled="project.status !== 'READY' || importingId === project.id || deletingId === project.id"
                    @click="sourceProject = project"
                  >结构</button>
                  <button
                    type="button"
                    :disabled="project.status !== 'READY' || importingId === project.id || deletingId === project.id"
                    @click="diffProject = project"
                  >Diff</button>
                  <button
                    type="button"
                    :disabled="project.status !== 'READY' || importingId === project.id || deletingId === project.id"
                    @click="analysisProject = project"
                  >静态分析</button>
                  <button
                    type="button"
                    :disabled="project.status !== 'READY' || importingId === project.id || deletingId === project.id"
                    @click="retrievalProject = project"
                  >检索</button>
                  <button type="button" :disabled="importingId === project.id || deletingId === project.id" @click="openEdit(project)">编辑</button>
                  <button class="danger" type="button" :disabled="deletingId === project.id || importingId === project.id" @click="removeProject(project)">
                    {{ deletingId === project.id ? '删除中' : '删除' }}
                  </button>
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
      @close="retrievalProject = null"
    />
  </div>
</template>
