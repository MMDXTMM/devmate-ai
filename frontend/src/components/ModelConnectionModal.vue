<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useModalFocus } from '../composables/useModalFocus'
import { ApiError } from '../services/apiClient'
import { modelConnectionApi } from '../services/modelConnectionApi'
import type { ModelConnectionTest, ModelProvider } from '../types/modelConnection'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const providers = ref<ModelProvider[]>([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const switchingProvider = ref('')
const deleting = ref(false)
const deleteTarget = ref<ModelProvider>()
const cancelDeleteButton = ref<HTMLButtonElement>()
const connectionsHeading = ref<HTMLHeadingElement>()
let deleteTrigger: HTMLElement | undefined
const error = ref('')
const feedback = ref('')
const result = ref<ModelConnectionTest>()
const draft = reactive<Record<string, { model: string; apiKey: string; baseUrl: string }>>({})
const editingProvider = ref('')
const configuredProviders = computed(() => providers.value.filter(item => item.configured))
const availableProviders = computed(() => providers.value.filter(item => !item.configured))
const activeProvider = computed(() => providers.value.find(item => item.active && item.configured))
const editor = computed(() => providers.value.find(item => item.provider === editingProvider.value))
const busy = computed(() => saving.value || testing.value || deleting.value || !!deleteTarget.value)
const dialogRef = useModalFocus(() => props.open, () => emit('close'))
let openEpoch = 0
function current(epoch: number) { return props.open && epoch === openEpoch }
function clearKeys() { Object.values(draft).forEach(item => { item.apiKey = '' }) }
onBeforeUnmount(() => { openEpoch += 1; clearKeys() })

function message(value: unknown) { return value instanceof ApiError ? value.message : '操作失败，请稍后重试' }
async function load() {
  const epoch = openEpoch
  loading.value = true; error.value = ''; feedback.value = ''; result.value = undefined
  try {
    const loaded = await modelConnectionApi.list()
    if (!current(epoch)) return
    providers.value = loaded
    providers.value.forEach((item) => { draft[item.provider] = { model: item.selectedModel, apiKey: '', baseUrl: item.baseUrl } })
    editingProvider.value = loaded.some(item => item.configured) ? '' : loaded[0]?.provider ?? ''
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) loading.value = false }
}
function editConnection(item: ModelProvider) {
  if (busy.value) return
  clearKeys()
  draft[item.provider] = { model: item.selectedModel, apiKey: '', baseUrl: item.baseUrl }
  editingProvider.value = item.provider
  error.value = ''; feedback.value = ''
}
function closeEditor() {
  if (busy.value) return
  clearKeys()
  editingProvider.value = ''
  error.value = ''
}
function changeEditor(event: Event) {
  const item = providers.value.find(provider => provider.provider === (event.target as HTMLSelectElement).value)
  if (item) editConnection(item)
}
async function useSavedConnection(item: ModelProvider) {
  if (busy.value || item.active || !item.configured) return
  const epoch = openEpoch
  saving.value = true; switchingProvider.value = item.provider
  error.value = ''; feedback.value = ''; result.value = undefined
  try {
    const updated = await modelConnectionApi.update(item.provider, item.selectedModel, '')
    if (!current(epoch)) return
    providers.value = updated
    clearKeys()
    editingProvider.value = ''
    feedback.value = `已启用 ${item.displayName} · ${item.selectedModel}`
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) { saving.value = false; switchingProvider.value = '' } }
}
async function activate(item: ModelProvider) {
  if (busy.value) return
  const epoch = openEpoch
  saving.value = true; error.value = ''; feedback.value = ''; result.value = undefined
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
    if (saved) draft[item.provider] = { model: saved.selectedModel, apiKey: '', baseUrl: saved.baseUrl }
    editingProvider.value = ''
    feedback.value = '连接已保存并启用，可测试连接或返回项目使用。'
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) saving.value = false }
}
async function testConnection() {
  if (busy.value) return
  const epoch = openEpoch
  testing.value = true; error.value = ''; feedback.value = ''; result.value = undefined
  try {
    const tested = await modelConnectionApi.test()
    if (current(epoch)) result.value = tested
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) testing.value = false }
}
async function requestDelete(item: ModelProvider, event: Event) {
  if (busy.value || !item.configured) return
  deleteTrigger = event.currentTarget as HTMLElement
  clearKeys()
  editingProvider.value = ''
  error.value = ''; feedback.value = ''; result.value = undefined
  deleteTarget.value = item
  await nextTick()
  cancelDeleteButton.value?.focus()
}
async function cancelDelete() {
  if (deleting.value) return
  deleteTarget.value = undefined
  error.value = ''
  await nextTick()
  deleteTrigger?.focus()
}
async function confirmDelete() {
  const target = deleteTarget.value
  if (!target || deleting.value || saving.value || testing.value) return
  const epoch = openEpoch
  deleting.value = true; error.value = ''
  try {
    const updated = await modelConnectionApi.remove(target.provider)
    if (!current(epoch)) return
    providers.value = updated
    clearKeys()
    updated.forEach(item => { draft[item.provider] = { model: item.selectedModel, apiKey: '', baseUrl: item.baseUrl } })
    deleteTarget.value = undefined
    editingProvider.value = updated.some(item => item.configured) ? '' : updated[0]?.provider ?? ''
    feedback.value = `已删除 ${target.displayName} 的配置。${target.active ? '请重新选择或添加一个连接后使用 AI。' : '其他连接可以继续使用。'}`
    await nextTick()
    connectionsHeading.value?.focus()
  } catch (value) { if (current(epoch)) error.value = message(value) }
  finally { if (current(epoch)) deleting.value = false }
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
  editingProvider.value = ''; switchingProvider.value = ''; error.value = ''; feedback.value = ''
  deleteTarget.value = undefined; deleting.value = false; deleteTrigger = undefined
  loading.value = false; saving.value = false; testing.value = false
  if (open) void load()
  else result.value = undefined
}, { immediate: true })
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section :ref="dialogRef" class="modal model-modal" role="dialog" aria-modal="true" aria-labelledby="model-title" tabindex="-1">
      <header class="modal-header">
        <span class="surface-icon" aria-hidden="true">🔌</span>
        <div><p class="eyebrow">MODEL CONNECTIONS</p><h2 id="model-title">大模型连接中心</h2><p>选择已保存的连接，或添加新的连接。</p></div>
        <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <div class="model-connection-content" :aria-busy="loading || busy">
        <div v-if="error" class="notice error" role="alert">{{ error }}<button v-if="!providers.length && !loading" class="button secondary" type="button" @click="load">重新读取</button></div>
        <div v-if="feedback" class="notice success" role="status">{{ feedback }}</div>
        <div v-if="loading" class="state-box"><span class="loader"></span><p>正在读取模型配置…</p></div>
        <template v-else-if="providers.length">
          <section class="saved-connections" aria-labelledby="saved-connections-title">
            <div class="connections-heading">
              <div><h3 id="saved-connections-title" ref="connectionsHeading" tabindex="-1">我的连接 <span>{{ configuredProviders.length }}</span></h3><p>每行一个已保存的连接，点选即可启用。</p></div>
              <button v-if="availableProviders.length" class="button secondary add-connection" type="button" :disabled="busy" @click="editConnection(availableProviders[0]!)">＋ 添加连接</button>
            </div>
            <div v-if="configuredProviders.length" class="saved-connection-list" role="group" aria-label="选择当前启用的模型连接">
              <div v-for="item in configuredProviders" :key="item.provider" class="saved-connection" :class="{ active: item.active }">
                <button class="saved-connection-choice" type="button" :aria-pressed="item.active" :disabled="busy" :aria-label="`启用 ${item.displayName} · ${item.selectedModel}`" @click="useSavedConnection(item)">
                  <span class="connection-indicator" aria-hidden="true"></span>
                  <strong class="connection-name" :title="item.displayName">{{ item.displayName }}</strong>
                  <span class="connection-model" :title="item.selectedModel">{{ item.selectedModel }}</span>
                  <span class="connection-state">{{ switchingProvider === item.provider ? '切换中…' : item.active ? '当前启用' : '已保存' }}</span>
                </button>
                <button class="connection-edit" type="button" :disabled="busy" :aria-label="`编辑 ${item.displayName} 连接`" @click="editConnection(item)">编辑</button>
                <button class="connection-edit connection-delete" type="button" :disabled="busy" :aria-label="`删除 ${item.displayName} 连接`" @click="requestDelete(item, $event)">删除</button>
              </div>
            </div>
            <div v-else class="connection-empty"><strong>还没有保存连接</strong><p>在下方选择服务商，填写 API Key 后保存，即可开始使用 AI。</p></div>
            <p v-if="configuredProviders.length" class="connection-hint">切换会保留已保存的 Key；连接是否可用，可点击底部按钮测试。</p>
            <section v-if="deleteTarget" class="connection-delete-confirm" role="group" aria-labelledby="delete-connection-title">
              <h4 id="delete-connection-title">删除 {{ deleteTarget.displayName }} 的连接配置？</h4>
              <p>{{ deleteTarget.selectedModel }} · 仅移除 DevMate 保存的配置和 Key，重新添加需填写 Key。项目和历史记录保留。</p>
              <p v-if="deleteTarget.active">这是当前启用的连接，删除后需重新选择或添加连接才能发起新的 AI 请求。</p>
              <p>已开始的任务可能继续使用提交时的配置；删除这里的配置不会撤销服务商签发的 Key。</p>
              <div class="connection-editor-actions"><button ref="cancelDeleteButton" class="button secondary" type="button" :disabled="deleting" @click="cancelDelete">保留连接</button><button class="button danger" type="button" :disabled="deleting" @click="confirmDelete">{{ deleting ? '正在删除…' : '确认删除配置' }}</button></div>
            </section>
          </section>

          <form v-if="editor" class="model-connection-editor" aria-labelledby="connection-editor-title" @submit.prevent="activate(editor)">
            <div class="connections-heading"><h3 id="connection-editor-title">{{ editor.configured ? '编辑连接' : '添加连接' }}</h3><button class="connection-edit" type="button" :disabled="busy" @click="closeEditor">收起</button></div>
            <label><span>服务商</span><select :value="editingProvider" :disabled="busy" @change="changeEditor"><option v-for="item in providers" :key="item.provider" :value="item.provider">{{ item.displayName }}{{ item.configured ? '（已保存）' : '' }}</option></select></label>
            <template v-if="editor.provider === 'COMPATIBLE'">
              <p class="relay-intro">按中转站提供的连接信息填写，网站首页地址不一定是 API 请求地址。</p>
              <label><span>接口协议</span><input value="OpenAI Chat Completions" readonly aria-label="接口协议" /></label>
              <label><span>API 请求基础地址</span><input v-model="draft[editor.provider].baseUrl" :disabled="busy" type="url" required placeholder="https://网关域名/v1" maxlength="500" autocomplete="off" :list="'relay-urls-' + editor.provider" /></label>
              <datalist :id="'relay-urls-' + editor.provider"><option v-for="url in editor.allowedBaseUrls || [editor.baseUrl]" :key="url" :value="url" /></datalist>
              <small>通常以 /v1 结尾，也支持服务商指定的其他前缀。可粘贴完整 /chat/completions 地址，保存时自动整理。</small>
              <p class="relay-request-preview">实际请求：POST {{ requestPreview(draft[editor.provider].baseUrl) }}</p>
              <label><span>模型 ID</span><input v-model="draft[editor.provider].model" :disabled="busy" type="text" required :list="'relay-models-' + editor.provider" placeholder="填写服务商实际开放的模型 ID" maxlength="100" autocomplete="off" /></label>
              <datalist :id="'relay-models-' + editor.provider"><option v-for="model in editor.models" :key="model" :value="model" /></datalist>
              <small>填写 API 模型 ID，保持大小写；不是连接名称。可选择建议值或手动填写服务商支持的其他模型。</small>
              <details class="relay-format-guide"><summary>如何对照中转站配置填写？</summary><ul><li>API Key → 本页 API Key</li><li>base_url / API 请求地址 → 请求基础地址</li><li>model / 默认模型 → 模型 ID</li><li>wire_api=responses → 协议不同，请先确认服务商支持 Chat Completions</li></ul><p>名称、备注、官网链接不参与调用；子代理、自动审批模型和本地 MCP 配置属于客户端设置，无需粘贴。不要上传整份 auth.json 或 config.toml。</p><p>请求地址只允许服务端批准的中转站。更换到其他服务时，管理员需要先将新地址加入受信任名单；更换地址必须重新填写对应 Key。</p></details>
            </template>
            <p v-else class="model-base-url">请求地址：{{ editor.baseUrl }}</p>
            <label v-if="editor.provider !== 'COMPATIBLE'"><span>模型</span><select v-model="draft[editor.provider].model" :disabled="busy"><option v-for="model in editor.models" :key="model">{{ model }}</option></select></label>
            <label><span>API Key</span><input v-model="draft[editor.provider].apiKey" :disabled="busy" type="password" :required="!editor.configured" maxlength="500" autocomplete="off" :placeholder="editor.configured ? '留空则保留已加密保存的 Key' : '粘贴该服务商的 API Key'" /></label>
            <div class="connection-editor-actions"><button class="button secondary" type="button" :disabled="busy" @click="closeEditor">取消</button><button class="button primary" type="submit" :disabled="busy">{{ saving ? '正在保存…' : '保存并启用' }}</button></div>
          </form>
        </template>

        <details class="model-setup-guide">
          <summary>第一次配置？查看三步说明</summary>
          <ol><li>在模型厂商或中转站创建 API Key。网页聊天会员与 API 额度分别管理。</li><li>点击“添加连接”，选择服务商并填写模型和 Key，再点击“保存并启用”。</li><li>点击“测试当前连接”，成功后回到项目生成理解报告、提问或审查。</li></ol>
          <p>获取 Key：<a href="https://platform.deepseek.com/api_keys" target="_blank" rel="noopener noreferrer">DeepSeek</a> · <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener noreferrer">阿里云百炼</a> · <a href="https://platform.openai.com/api-keys" target="_blank" rel="noopener noreferrer">OpenAI</a></p>
          <p>使用公司网关或第三方中转站时，选择“OpenAI 兼容／中转站”，填写请求基础地址、模型 ID 和对应服务签发的 Key。当前支持 OpenAI Chat Completions；Responses 是另一种协议，请确认服务商支持 /chat/completions。</p>
          <small>检索索引使用独立的 Embedding 配置；切换聊天模型不会切换向量模型。</small>
        </details>
        <div v-if="result" class="notice success" role="status">连接成功。可以返回项目生成深度报告或向项目提问。<button class="button secondary" type="button" @click="emit('close')">返回项目</button><p class="model-test-result">{{ result.provider }} / {{ result.model }} · {{ result.latencyMs }}ms</p></div>
      </div>
      <footer class="model-modal-footer">
        <div class="connection-current"><strong>{{ activeProvider ? `当前：${activeProvider.displayName} · ${activeProvider.selectedModel}` : '尚未启用连接' }}</strong><p>Key 加密保存，不回显。测试会消耗少量 API 额度。</p></div>
        <button class="button primary" type="button" :disabled="busy || loading || !activeProvider" @click="testConnection">{{ testing ? '正在测试…' : '测试当前连接' }}</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop { z-index: 160; }
.model-modal { width: min(900px, 100%); max-height: calc(100dvh - 48px); display: flex; flex-direction: column; overflow: hidden; }
.modal-header, .model-modal-footer { flex: 0 0 auto; }
.model-connection-content { min-height: 0; overflow-y: auto; padding: 24px; display: flex; flex-direction: column; gap: 20px; }
.model-connection-content > .notice { margin: 0; }
.model-connection-content > .notice .button { margin-left: 10px; }
.connections-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }
.connections-heading h3 { margin: 0; font-size: 16px; color: var(--ink); }
.connections-heading h3 span { margin-left: 5px; padding: 2px 7px; font-size: 12px; background: var(--accent-soft); color: var(--accent); border-radius: 6px; }
.connections-heading p, .connection-hint, .connection-empty p { margin: 7px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.add-connection { white-space: nowrap; flex-shrink: 0; }
.saved-connection-list { display: flex; flex-direction: column; gap: 8px; }
.saved-connection { display: flex; align-items: center; gap: 12px; border: 1px solid var(--line); border-radius: 12px; padding: 0 16px; background: var(--surface); }
.saved-connection:hover { border-color: var(--accent-line); }
.saved-connection.active { background: var(--accent-soft); border-color: var(--accent-line); }
.saved-connection-choice { min-width: 0; flex: 1; display: grid; grid-template-columns: 18px minmax(0, 1fr) minmax(0, 1fr) 68px; align-items: center; gap: 14px; padding: 17px 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.connection-indicator { width: 16px; height: 16px; border: 1.5px solid var(--accent-line); border-radius: 50%; }
.active .connection-indicator { border-color: var(--accent); background: var(--accent); box-shadow: inset 0 0 0 3px var(--accent-soft); }
.connection-name, .connection-model { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.connection-model { color: var(--ink-soft); }
.connection-state { font-size: 11px; color: var(--muted); white-space: nowrap; }
.active .connection-state { color: var(--accent); font-weight: 700; }
.connection-edit { flex-shrink: 0; padding: 8px; border: 0; border-radius: 8px; color: var(--accent); background: transparent; font-size: 12px; cursor: pointer; }
.connection-edit:hover:not(:disabled) { background: var(--accent-soft); }
.connection-delete { color: #ad3447; }
.connection-delete-confirm { margin-top: 16px; padding: 16px; border: 1px solid #e7b7bf; border-radius: 12px; background: var(--surface); }
.connection-delete-confirm h4 { margin: 0 0 8px; font-size: 14px; }
.connection-delete-confirm p { margin: 8px 0; font-size: 12px; line-height: 1.7; color: var(--ink-soft); overflow-wrap: anywhere; }
.connection-edit:disabled, .saved-connection-choice:disabled { cursor: wait; opacity: .65; }
.connection-empty { border: 1px dashed var(--accent-line); border-radius: 12px; padding: 20px; color: var(--ink-soft); background: var(--surface); font-size: 14px; }
.connection-hint { margin-top: 12px; }
.model-connection-editor { display: grid; gap: 14px; padding: 20px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }
.model-connection-editor .connections-heading { margin: 0; }
.model-connection-editor label { display: grid; gap: 7px; font-size: 12px; font-weight: 700; }
.model-connection-editor :is(input, select) { min-width: 0; width: 100%; padding: 11px 12px; border: 1px solid var(--line); background: var(--surface); font-size: 13px; }
.model-connection-editor small { color: var(--muted); font-size: 12px; line-height: 1.7; }
.model-base-url { margin: 0; min-height: 0; }
.connection-editor-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.model-setup-guide { margin: 0; padding: 14px 16px; font-size: 12px; box-shadow: none; }
.model-setup-guide summary { cursor: pointer; font-weight: 600; }
.model-modal-footer { padding: 18px 24px; gap: 16px; }
.model-modal-footer .connection-current { min-width: 0; display: block; }
.connection-current strong { display: block; overflow-wrap: anywhere; font-size: 12px; }
.connection-current p { margin: 6px 0 0; line-height: 1.6; }
.model-modal-footer > .button { white-space: nowrap; flex-shrink: 0; }
.relay-intro, .relay-format-guide { font-size: 13px; line-height: 1.7; color: #625c72; }
.relay-intro { margin: 0; }
.relay-request-preview { margin: 0; font-size: 12px; overflow-wrap: anywhere; background: #f1edf8; padding: 10px; border-radius: 8px; }
.relay-format-guide summary { cursor: pointer; font-weight: 600; }
@media (max-width: 600px) {
  .model-modal { max-height: calc(100dvh - 20px); }
  .model-connection-content { padding: 16px; gap: 16px; }
  .connections-heading { gap: 10px; }
  .connections-heading p { font-size: 11px; }
  .add-connection { padding: 9px 10px; font-size: 12px; }
  .saved-connection { padding: 0 10px; gap: 6px; }
  .saved-connection-choice { grid-template-columns: 16px minmax(0, 1fr) auto; gap: 8px; padding: 13px 0; }
  .connection-model { grid-column: 2; grid-row: 2; font-size: 11px; }
  .connection-state { grid-column: 3; grid-row: 1 / 3; }
  .model-connection-editor { padding: 16px; }
  .model-modal-footer { padding: 16px; align-items: stretch; }
}
</style>
