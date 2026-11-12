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
const draft = reactive<Record<string, { model: string; apiKey: string; baseUrl: string }>>({})
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
    providers.value.forEach((item) => { draft[item.provider] = { model: item.selectedModel, apiKey: '', baseUrl: item.baseUrl } })
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) loading.value = false }
}
async function activate(item: ModelProvider) {
  if (saving.value || testing.value) return
  const epoch = openEpoch
  saving.value = true; error.value = ''; result.value = undefined
  try {
    const value = draft[item.provider]
    if (item.provider === 'COMPATIBLE' && (!value.baseUrl.trim() || !value.model.trim())) {
      error.value = '请填写 API 请求基础地址和模型 ID'
      return
    }
    const updated = item.provider === 'COMPATIBLE'
      ? await modelConnectionApi.update(item.provider, value.model.trim(), value.apiKey,
        { baseUrl: value.baseUrl.trim(), protocol: 'CHAT_COMPLETIONS' })
      : await modelConnectionApi.update(item.provider, value.model, value.apiKey)
    if (!current(epoch)) return
    providers.value = updated
    draft[item.provider].apiKey = ''
    const saved = updated.find(provider => provider.provider === item.provider)
    if (saved) draft[item.provider].baseUrl = saved.baseUrl
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
function requestPreview(value: string) {
  try {
    const url = new URL(value.trim())
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) return '请填写无凭据的 HTTPS 地址'
    return url.origin + url.pathname.replace(/\/+$|\/(chat\/completions|responses)\/?$/g, '') + '/chat/completions'
  } catch { return '填写基础地址后显示' }
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
        <span class="surface-icon" aria-hidden="true">🔌</span>
        <div><p class="eyebrow">MODEL CONNECTIONS</p><h2 id="model-title">大模型连接中心</h2><p>配置、切换并检查项目理解与代码审查使用的模型。</p></div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <section class="model-setup-guide">
        <b>首次使用只需三步</b>
        <ol><li>在模型厂商或中转站创建 API Key。网页聊天会员与 API 额度分别管理。</li><li>选择官方厂商，或为中转站填写请求地址、模型 ID 和 Key，点击“保存并启用”，配置绑定当前登录账户。</li><li>点击“测试当前连接”，成功后回到项目生成理解报告、提问或审查。</li></ol>
        <p>获取 Key：<a href="https://platform.deepseek.com/api_keys" target="_blank" rel="noopener noreferrer">DeepSeek</a> · <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener noreferrer">阿里云百炼</a> · <a href="https://platform.openai.com/api-keys" target="_blank" rel="noopener noreferrer">OpenAI</a></p>
        <small>检索索引使用独立的 Embedding 配置；切换聊天模型不会切换向量模型。测试会发送一次小请求并消耗少量 API 额度。</small>
        <p>使用公司网关或第三方中转站时，选择“OpenAI 兼容／中转站”。需要请求基础地址、模型 ID 和对应服务签发的 Key。当前支持 OpenAI Chat Completions 协议；截图或配置文件中的 Responses 是另一种协议，请向中转站确认也支持 /chat/completions。</p>
      </section>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <div v-if="loading" class="state-box"><span class="loader"></span><p>正在读取模型配置…</p></div>
      <div v-else class="model-provider-grid">
        <article v-for="item in providers" :key="item.provider" :class="{ active: item.active }">
          <div class="model-provider-heading"><div><small>{{ item.provider }}</small><h3>{{ item.displayName }}</h3></div><span>{{ item.active ? '当前启用' : item.configured ? '已配置' : '未配置' }}</span></div>
          <template v-if="item.provider === 'COMPATIBLE'">
            <p class="relay-intro">按中转站提供的连接信息填写，网站首页地址不一定是 API 请求地址。</p>
            <label><span>接口协议</span><input value="OpenAI Chat Completions" readonly aria-label="接口协议" /></label>
            <label><span>API 请求基础地址</span><input v-model="draft[item.provider].baseUrl" :disabled="saving || testing" type="url" placeholder="https://网关域名/v1" maxlength="500" autocomplete="off" :list="'relay-urls-' + item.provider" /></label>
            <datalist :id="'relay-urls-' + item.provider"><option v-for="url in item.allowedBaseUrls || [item.baseUrl]" :key="url" :value="url" /></datalist>
            <small>通常以 /v1 结尾，也支持服务商指定的其他前缀。可粘贴完整 /chat/completions 地址，保存时自动整理。</small>
            <p class="relay-request-preview">实际请求：POST {{ requestPreview(draft[item.provider].baseUrl) }}</p>
            <label><span>模型 ID</span><input v-model="draft[item.provider].model" :disabled="saving || testing" type="text" :list="'relay-models-' + item.provider" placeholder="填写服务商实际开放的模型 ID" maxlength="100" autocomplete="off" /></label>
            <datalist :id="'relay-models-' + item.provider"><option v-for="model in item.models" :key="model" :value="model" /></datalist>
            <small>填写 API 模型 ID，保持大小写；不是连接名称。可选择建议值或手动填写服务商支持的其他模型。</small>
            <details class="relay-format-guide"><summary>如何对照中转站配置填写？</summary><ul><li>API Key → 本页 API Key</li><li>base_url / API 请求地址 → 请求基础地址</li><li>model / 默认模型 → 模型 ID</li><li>wire_api=responses → 协议不同，请先确认服务商支持 Chat Completions</li></ul><p>名称、备注、官网链接不参与调用；子代理、自动审批模型和本地 MCP 配置属于客户端设置，无需粘贴。不要上传整份 auth.json 或 config.toml。</p><p>请求地址只允许服务端批准的中转站。更换到其他服务时，管理员需要先将新地址加入受信任名单；更换地址必须重新填写对应 Key。</p></details>
          </template>
          <p v-else class="model-base-url">{{ item.baseUrl }}</p>
          <label v-if="item.provider !== 'COMPATIBLE'"><span>模型</span><select v-model="draft[item.provider].model" :disabled="saving || testing"><option v-for="model in item.models" :key="model">{{ model }}</option></select></label>
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

<style scoped>
.modal-backdrop { z-index: 160; }
.relay-intro, .relay-format-guide { font-size: 13px; line-height: 1.7; color: #625c72; }
.relay-request-preview { font-size: 12px; overflow-wrap: anywhere; background: #f1edf8; padding: 10px; border-radius: 8px; }
.relay-format-guide { margin: 12px 0; }
.relay-format-guide summary { cursor: pointer; font-weight: 600; }
.model-provider-grid small { display: block; line-height: 1.6; }
</style>
