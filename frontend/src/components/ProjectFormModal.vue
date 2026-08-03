<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { Project, ProjectForm, SourceType } from '../types/project'

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
  defaultBranch: 'main',
})

const form = reactive<ProjectForm>(emptyForm())
const error = reactive({ message: '' })
const title = computed(() => (props.project ? '编辑项目' : '新建项目'))

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

function changeSourceType(value: SourceType) {
  form.sourceType = value
  if (value === 'GIT' && !form.defaultBranch) form.defaultBranch = 'main'
}

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

        <fieldset>
          <legend>源码类型</legend>
          <div class="source-options">
            <button
              v-for="type in (['GIT', 'LOCAL', 'UPLOAD'] as SourceType[])"
              :key="type"
              class="source-option"
              :class="{ active: form.sourceType === type }"
              type="button"
              @click="changeSourceType(type)"
            >
              {{ type }}
            </button>
          </div>
        </fieldset>

        <label>
          <span>{{ form.sourceType === 'GIT' ? 'Git 仓库地址 *' : '源码位置' }}</span>
          <input
            v-model="form.sourceLocation"
            maxlength="1000"
            :placeholder="form.sourceType === 'GIT' ? 'https://github.com/user/repository.git' : '填写源码位置'"
          />
        </label>

        <label>
          <span>默认分支</span>
          <input v-model="form.defaultBranch" maxlength="100" placeholder="main" />
        </label>

        <p v-if="error.message" class="form-error" role="alert">{{ error.message }}</p>

        <footer class="modal-actions">
          <button class="button secondary" type="button" :disabled="saving" @click="emit('close')">取消</button>
          <button class="button primary" type="submit" :disabled="saving">
            {{ saving ? '保存中…' : '保存项目' }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
