<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ApiError } from '../services/apiClient'
import { modelConnectionApi } from '../services/modelConnectionApi'
import type { ModelConnectionTest, ModelProvider } from '../types/modelConnection'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const providers = ref<ModelProvider[]>([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const result = ref<ModelConnectionTest>()
const draft = reactive<Record<string, { model: string; apiKey: string }>>({})

function message(value: unknown) { return value instanceof ApiError ? value.message : '操作失败，请稍后重试' }
async function load() {
  loading.value = true; error.value = ''
  try {
    providers.value = await modelConnectionApi.list()
    providers.value.forEach((item) => { draft[item.provider] ??= { model: item.selectedModel, apiKey: '' } })
  } catch (value) { error.value = message(value) } finally { loading.value = false }
}
async function activate(item: ModelProvider) {
  saving.value = true; error.value = ''; result.value = undefined
  try { providers.value = await modelConnectionApi.update(item.provider, draft[item.provider].model, draft[item.provider].apiKey); draft[item.provider].apiKey = '' }
  catch (value) { error.value = message(value) } finally { saving.value = false }
}
async function testConnection() {
  testing.value = true; error.value = ''
  try { result.value = await modelConnectionApi.test() } catch (value) { error.value = message(value) }
  finally { testing.value = false }
}
watch(() => props.open, (open) => { if (open) void load() }, { immediate: true })
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal model-modal" role="dialog" aria-modal="true" aria-labelledby="model-title">
      <header class="modal-header">
        <div><p class="eyebrow">MODEL CONNECTIONS</p><h2 id="model-title">大模型连接中心</h2><p>配置、切换并检查项目理解与代码审查使用的模型。</p></div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <div v-if="loading" class="state-box"><span class="loader"></span><p>正在读取模型配置…</p></div>
      <div v-else class="model-provider-grid">
        <article v-for="item in providers" :key="item.provider" :class="{ active: item.active }">
          <div class="model-provider-heading"><div><small>{{ item.provider }}</small><h3>{{ item.displayName }}</h3></div><span>{{ item.active ? '当前启用' : item.configured ? '已配置' : '未配置' }}</span></div>
          <p class="model-base-url">{{ item.baseUrl }}</p>
          <label><span>模型</span><select v-model="draft[item.provider].model"><option v-for="model in item.models" :key="model">{{ model }}</option></select></label>
          <label><span>API Key</span><input v-model="draft[item.provider].apiKey" type="password" autocomplete="off" :placeholder="item.configured ? '留空则保留已加密保存的 Key' : '保存后将加密绑定当前账户'" /></label>
          <button class="button secondary" type="button" :disabled="saving" @click="activate(item)">{{ item.active ? '更新配置' : '保存并启用' }}</button>
        </article>
      </div>
      <footer class="model-modal-footer">
        <p>API Key 加密后绑定当前账户；页面不会回显明文，其他账户无法使用。</p>
        <div><span v-if="result" class="model-test-result">{{ result.provider }} / {{ result.model }} · {{ result.latencyMs }}ms</span><button class="button primary" type="button" :disabled="testing || !providers.some(item => item.active)" @click="testConnection">{{ testing ? '正在测试…' : '测试当前连接' }}</button></div>
      </footer>
    </section>
  </div>
</template>
