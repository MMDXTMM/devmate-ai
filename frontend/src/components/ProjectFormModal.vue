<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { Project, ProjectForm } from '../types/project'

const props = defineProps<{
  open: boolean
  project?: Project | null
  saving: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [form: ProjectForm]
}>()

const emptyForm = (): ProjectForm => ({
  name: '',
  description: '',
  sourceType: 'GIT',
  sourceLocation: '',
  defaultBranch: '',
})

const form = reactive<ProjectForm>(emptyForm())
const error = reactive({ message: '' })
const title = computed(() => (props.project ? '编辑项目' : '导入项目'))

watch(
  () => [props.open, props.project] as const,
  () => {
    if (!props.open) return
    Object.assign(
      form,
      props.project
        ? {
            name: props.project.name,
            description: props.project.description ?? '',
            sourceType: props.project.sourceType,
            sourceLocation: props.project.sourceLocation ?? '',
            defaultBranch: props.project.defaultBranch ?? '',
          }
        : emptyForm(),
    )
    error.message = ''
  },
  { immediate: true },
)

function submit() {
  const name = form.name.trim()
  const sourceLocation = form.sourceLocation.trim()
  if (!name) {
    error.message = '请输入项目名称'
    return
  }
  if (form.sourceType === 'GIT' && !sourceLocation) {
    error.message = 'Git 项目必须填写仓库地址'
    return
  }
  error.message = ''
  emit('submit', {
    name,
    description: form.description.trim(),
    sourceType: form.sourceType,
    sourceLocation,
    defaultBranch: form.defaultBranch.trim(),
  })
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" role="presentation" @click.self="emit('close')">
    <section class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <header class="modal-header">
        <span class="surface-icon" aria-hidden="true">📦</span>
        <div>
          <p class="eyebrow">PROJECT CONFIGURATION</p>
          <h2>{{ title }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <form class="project-form" @submit.prevent="submit">
        <label>
          <span>项目名称 <b>*</b></span>
          <input v-model="form.name" maxlength="100" placeholder="例如：devmate-ai" />
        </label>

        <label>
          <span>项目描述</span>
          <textarea v-model="form.description" maxlength="500" rows="3" placeholder="简要描述项目用途" />
        </label>

        <p v-if="!project" class="form-context">粘贴仓库主页的 HTTPS 地址即可。支持 Java、Python、JavaScript/TypeScript、Go 等常见文本项目，优先阅读 README，再结合源码生成导览。Java 提供额外的 AST 与代码审查能力。公开仓库可直接导入；私有仓库需要服务端配置只读 Git Token，模型 API Key 不能用于读取仓库。</p>

        <label>
          <span>{{ form.sourceType === 'GIT' ? 'Git 仓库地址 *' : '源码位置' }}</span>
          <input
            v-model="form.sourceLocation"
            maxlength="1000"
            :placeholder="form.sourceType === 'GIT' ? 'https://github.com/user/repository.git' : '填写源码位置'"
          />
        </label>

        <label>
          <span>导入分支（选填）</span>
          <input v-model="form.defaultBranch" maxlength="100" placeholder="留空自动使用仓库默认分支" />
        </label>

        <p v-if="error.message" class="form-error" role="alert">{{ error.message }}</p>

        <footer class="modal-actions">
          <button class="button secondary" type="button" :disabled="saving" @click="emit('close')">取消</button>
          <button class="button primary" type="submit" :disabled="saving">
            {{ saving ? '处理中…' : project ? '保存修改' : '导入并解析' }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
