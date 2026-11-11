<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
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
let openEpoch = 0
function current(epoch: number) { return props.open && epoch === openEpoch }
function clearKeys() { Object.values(draft).forEach(item => { item.apiKey = '' }) }
onBeforeUnmount(() => { openEpoch += 1; clearKeys() })

function message(value: unknown) { return value instanceof ApiError ? value.message : '操作失败，请稍后重试' }
async function load() {
  const epoch = openEpoch
  loading.value = true; error.value = ''; result.value = undefined
  try {
    const loaded = await modelConnectionApi.list()
    if (!current(epoch)) return
    providers.value = loaded
    providers.value.forEach((item) => { draft[item.provider] = { model: item.selectedModel, apiKey: '' } })
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) loading.value = false }
}
async function activate(item: ModelProvider) {
  if (saving.value || testing.value) return
  const epoch = openEpoch
  saving.value = true; error.value = ''; result.value = undefined
  try {
    const updated = await modelConnectionApi.update(item.provider, draft[item.provider].model, draft[item.provider].apiKey)
    if (!current(epoch)) return
    providers.value = updated
    draft[item.provider].apiKey = ''
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) saving.value = false }
}
async function testConnection() {
  if (testing.value || saving.value) return
  const epoch = openEpoch
  testing.value = true; error.value = ''; result.value = undefined
  try {
    const tested = await modelConnectionApi.test()
    if (current(epoch)) result.value = tested
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) testing.value = false }
}
watch(() => props.open, (open) => {
  openEpoch += 1
  clearKeys()
  providers.value = []
  loading.value = false; saving.value = false; testing.value = false
  if (open) void load()
  else result.value = undefined
}, { immediate: true })
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal model-modal" role="dialog" aria-modal="true" aria-labelledby="model-title">
      <header class="modal-header">
        <div><p class="eyebrow">MODEL CONNECTIONS</p><h2 id="model-title">大模型连接中心</h2><p>配置、切换并检查项目理解与代码审查使用的模型。</p></div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <section class="model-setup-guide">
        <b>首次使用只需三步</b>
        <ol><li>在模型厂商或中转站创建 API Key。网页聊天会员与 API 额度分别管理。</li><li>在下方填写 Key，点击“保存并启用”，配置绑定当前登录账户。</li><li>点击“测试当前连接”，成功后回到项目生成理解报告、提问或审查。</li></ol>
        <p>获取 Key：<a href="https://platform.deepseek.com/api_keys" target="_blank" rel="noopener noreferrer">DeepSeek</a> · <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener noreferrer">阿里云百炼</a> · <a href="https://platform.openai.com/api-keys" target="_blank" rel="noopener noreferrer">OpenAI</a></p>
        <small>检索索引使用独立的 Embedding 配置；切换聊天模型不会切换向量模型。测试会发送一次小请求并消耗少量 API 额度。</small>
        <p>使用公司网关或第三方中转站时，选择“OpenAI 兼容／中转站”。支持 OpenAI Chat Completions 协议，Key 须由对应服务签发；API 地址和模型名单由服务端配置，可更换为其他兼容服务。</p>
      </section>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <div v-if="loading" class="state-box"><span class="loader"></span><p>正在读取模型配置…</p></div>
      <div v-else class="model-provider-grid">
        <article v-for="item in providers" :key="item.provider" :class="{ active: item.active }">
          <div class="model-provider-heading"><div><small>{{ item.provider }}</small><h3>{{ item.displayName }}</h3></div><span>{{ item.active ? '当前启用' : item.configured ? '已配置' : '未配置' }}</span></div>
          <p class="model-base-url">{{ item.baseUrl }}</p>
          <label><span>模型</span><select v-model="draft[item.provider].model" :disabled="saving || testing"><option v-for="model in item.models" :key="model">{{ model }}</option></select></label>
          <label><span>API Key</span><input v-model="draft[item.provider].apiKey" :disabled="saving || testing" type="password" autocomplete="off" :placeholder="item.configured ? '留空则保留已加密保存的 Key' : '保存后将加密绑定当前账户'" /></label>
          <button class="button secondary" type="button" :disabled="saving || testing" @click="activate(item)">{{ item.active ? '更新配置' : '保存并启用' }}</button>
        </article>
      </div>
      <div v-if="result" class="notice success" role="status">连接成功。可以返回项目生成深度报告或向项目提问。<button class="button secondary" type="button" @click="emit('close')">返回项目</button></div>
      <footer class="model-modal-footer">
        <p>API Key 加密后绑定当前账户；页面不会回显明文，其他账户无法使用。</p>
        <div><span v-if="result" class="model-test-result">{{ result.provider }} / {{ result.model }} · {{ result.latencyMs }}ms</span><button class="button primary" type="button" :disabled="testing || saving || loading || !providers.some(item => item.active && item.configured)" @click="testConnection">{{ testing ? '正在测试…' : '测试当前连接' }}</button></div>
      </footer>
    </section>
  </div>
</template>
