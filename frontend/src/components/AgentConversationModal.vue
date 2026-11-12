<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useModelAccess } from '../composables/useModelAccess'
import { useStreamingText } from '../composables/useStreamingText'
import { ApiError } from '../services/apiClient'
import { conversationApi } from '../services/conversationApi'
import { modelConnectionApi } from '../services/modelConnectionApi'
import type { ModelProvider } from '../types/modelConnection'
import ModelAnswer from './ModelAnswer.vue'
import ConversationHistory from './ConversationHistory.vue'
import ChatEvidencePanel from './ChatEvidencePanel.vue'
import type { AgentConversation, ConversationEvidence, ConversationMessage } from '../types/conversation'

const requireModel = useModelAccess()
const checkingModel = ref(false)
const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
  initialQuestion?: string
}>()
const emit = defineEmits<{ close: []; configureModel: [] }>()
const conversations = ref<AgentConversation[]>([])
const selected = ref<AgentConversation>()
const messages = ref<ConversationMessage[]>([])
const question = ref('')
const loading = ref(false)
const creating = ref(false)
const streaming = ref(false)
const reveal = useStreamingText()
const streamedText = reveal.text
const historySearch = ref('')
const messageList = ref<HTMLElement>()
const followLatest = ref(true)
const providers = ref<ModelProvider[]>([])
const chosenModel = ref('')
const modelOptions = computed(() => providers.value.filter(item => item.configured).flatMap(item => item.models.map(model => ({ value: `${item.provider}/${model}`, provider: item.provider, model, label: `${item.displayName} · ${model}` }))))
const modelChoice = computed(() => modelOptions.value.find(item => item.value === chosenModel.value))
async function loadModels() {
  const epoch = dialogEpoch
  try {
    const result = await modelConnectionApi.list()
    if (!current(epoch)) return
    providers.value = result
    const active = result.find(item => item.active)
    chosenModel.value = active ? `${active.provider}/${active.selectedModel}` : ''
  } catch { /* History is readable without model configuration. */ }
}
function chooseModel(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (busy.value) return
  const draft = question.value
  startNewChat()
  question.value = draft
  chosenModel.value = value
}
const composer = ref<HTMLTextAreaElement>()
const sidebarOpen = ref(false)
const evidencePanel = ref<{ evidence: ConversationEvidence[]; title: string }>()
const managing = ref(false)
const management = ref<{ kind: 'rename' | 'delete'; item: AgentConversation }>()
const titleDraft = ref('')
const managementError = ref('')
const copiedMessage = ref('')
const copyNotice = ref('')
const activeMessageId = ref<string>()
const stopping = ref(false)
const isComposing = ref(false)
const runningMessage = computed(() => messages.value.find(item => item.status === 'RUNNING' && item.role === 'ASSISTANT'))
const busy = computed(() => streaming.value || creating.value || managing.value || !!runningMessage.value)
const turnNumber = (index: number) => messages.value.slice(0, index + 1).filter(item => item.role === 'USER').length
function resizeComposer() {
  const el = composer.value
  if (el) { el.style.height = 'auto'; el.style.height = `${Math.min(Math.max(48, el.scrollHeight), 176)}px` }
}
function fillQuestion(value: string) { question.value = value; void nextTick(() => composer.value?.focus()) }
function startNewChat() {
  if (busy.value || loading.value) return
  selectionEpoch += 1
  selected.value = undefined; messages.value = []; pendingAttempt = undefined
  question.value = ''; errorMessage.value = ''; evidencePanel.value = undefined; sidebarOpen.value = false
  void nextTick(() => composer.value?.focus())
}
function openManagement(kind: 'rename' | 'delete', item: AgentConversation) {
  management.value = { kind, item }; titleDraft.value = item.title; managementError.value = ''
}
async function manageConversation() {
  if (!props.projectId || !management.value || managing.value) return
  const { kind, item } = management.value
  if (kind === 'rename' && !titleDraft.value.trim()) { managementError.value = '请输入对话标题'; return }
  const epoch = dialogEpoch
  managing.value = true; managementError.value = ''
  try {
    if (kind === 'rename') {
      const updated = await conversationApi.rename(props.projectId, item.id, titleDraft.value.trim())
      if (!current(epoch)) return
      conversations.value = conversations.value.map(value => value.id === updated.id ? updated : value)
      if (selected.value?.id === updated.id) selected.value = updated
    } else {
      await conversationApi.delete(props.projectId, item.id)
      if (!current(epoch)) return
      conversations.value = conversations.value.filter(value => value.id !== item.id)
      if (selected.value?.id === item.id) {
        selectionEpoch += 1; selected.value = undefined; messages.value = []; pendingAttempt = undefined
        evidencePanel.value = undefined; question.value = ''
      }
    }
    management.value = undefined
  } catch (error) { if (current(epoch)) managementError.value = readableError(error) }
  finally { if (current(epoch)) managing.value = false }
}
async function copyAnswer(message: ConversationMessage) {
  try { await navigator.clipboard.writeText(message.content); copiedMessage.value = message.id; copyNotice.value = '回答已复制' }
  catch { copyNotice.value = '复制失败，请手动选择回答内容' }
}
async function stopAnswer() {
  const messageId = activeMessageId.value || runningMessage.value?.id
  if (!props.projectId || !selected.value || !messageId || stopping.value) return
  const epoch = dialogEpoch
  stopping.value = true
  try {
    await conversationApi.cancel(props.projectId, selected.value.id, messageId)
    if (current(epoch) && !streaming.value) await selectConversation(selected.value)
  } catch (error) { if (current(epoch)) { errorMessage.value = readableError(error); stopping.value = false } }
  finally { if (current(epoch) && !streaming.value) stopping.value = false }
}
watch(question, () => { void nextTick(resizeComposer) })
const suggestions = ['先用通俗的话介绍这个项目有哪些功能，以及用户怎样使用。', '选一条核心业务，按接口入口、处理步骤、数据变化和失败情况说明。', '我是新接手的开发者，应该先读哪些代码，怎样启动和验证？']
function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function onScroll() {
  const el = messageList.value
  if (el) followLatest.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}
async function scrollLatest(force = false) {
  await nextTick()
  const el = messageList.value
  if (el && (force || followLatest.value)) el.scrollTop = el.scrollHeight
  if (force) followLatest.value = true
}
function onComposerKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !isComposing.value && event.keyCode !== 229) { event.preventDefault(); void sendQuestion() }
}
watch(streamedText, () => { void scrollLatest() })
watch(() => messages.value.length, () => { void scrollLatest() })
const streamedEvidence = ref<ConversationEvidence[]>([])
const errorMessage = ref('')
let controller: AbortController | undefined
let dialogEpoch = 0
let selectionEpoch = 0
let historyPoll: ReturnType<typeof setTimeout> | undefined
watch(() => [runningMessage.value?.id, streaming.value, props.open] as const, ([id, streaming, open]) => {
  clearTimeout(historyPoll)
  if (!id || streaming || !open) return
  const epoch = dialogEpoch, selection = selectionEpoch, projectId = props.projectId, conversationId = selected.value?.id
  let remaining = 60
  const poll = async () => {
    if (!current(epoch) || selection !== selectionEpoch || !projectId || !conversationId || remaining-- <= 0) return
    try {
      const result = await conversationApi.messages(projectId, conversationId)
      if (!current(epoch) || selection !== selectionEpoch) return
      messages.value = result
      if (result.some(item => item.status === 'RUNNING')) historyPoll = setTimeout(poll, 2000)
    } catch (error) { if (current(epoch)) errorMessage.value = readableError(error) }
  }
  historyPoll = setTimeout(poll, 2000)
})
let pendingAttempt: { conversationId: string; question: string; key: string } | undefined
const statusLabels = { RUNNING: '回答中', COMPLETED: '已完成', FAILED: '回答失败', CANCELLED: '已停止' }
function current(epoch: number) { return props.open && epoch === dialogEpoch }
function readableError(error: unknown) {
  return error instanceof ApiError ? error.message : '项目对话操作失败，请稍后重试'
}

async function loadConversations() {
  if (!props.projectId) return
  const epoch = dialogEpoch
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await conversationApi.list(props.projectId)
    if (!current(epoch)) return
    conversations.value = result
    if (!selected.value && result.length) await selectConversation(result[0])
  } catch (error) {
    if (current(epoch)) errorMessage.value = readableError(error)
  } finally {
    if (current(epoch)) loading.value = false
  }
}

async function createConversation() {
  if (!props.projectId || busy.value) return
  const epoch = dialogEpoch
  selectionEpoch += 1
  creating.value = true
  errorMessage.value = ''
  try {
    const created = await conversationApi.create(props.projectId, undefined, modelChoice.value ? { provider: modelChoice.value.provider, model: modelChoice.value.model } : undefined)
    if (!current(epoch)) return
    conversations.value = [created, ...conversations.value]
    selected.value = created
    messages.value = []
    followLatest.value = true
    historySearch.value = ''
    pendingAttempt = undefined
  } catch (error) {
    if (current(epoch)) errorMessage.value = readableError(error)
  } finally {
    if (current(epoch)) creating.value = false
  }
}

async function selectConversation(conversation: AgentConversation) {
  if (!props.projectId || streaming.value || creating.value || managing.value) return
  const epoch = dialogEpoch
  const selection = ++selectionEpoch
  selected.value = conversation
  sidebarOpen.value = false
  evidencePanel.value = undefined
  followLatest.value = true
  messages.value = []
  errorMessage.value = ''
  loading.value = true
  try {
    const result = await conversationApi.messages(props.projectId, conversation.id)
    if (current(epoch) && selection === selectionEpoch) messages.value = result
  } catch (error) {
    if (current(epoch) && selection === selectionEpoch) errorMessage.value = readableError(error)
  } finally {
    if (current(epoch) && selection === selectionEpoch) loading.value = false
  }
}

async function sendQuestion() {
  if (!props.projectId || !question.value.trim() || busy.value || loading.value || checkingModel.value) return
  const epoch = dialogEpoch
  const projectId = props.projectId
  checkingModel.value = true
  try {
    if (!await requireModel('向项目 AI 发送问题') || !current(epoch)) return
  } finally { checkingModel.value = false }
  if (!selected.value) {
    await createConversation()
    if (!current(epoch) || !selected.value) return
  }
  const submitted = question.value.trim()
  const conversationId = selected.value.id
  // Reuse an uncertain request's idempotency key: a disconnected stream must not start another paid answer.
  const attempt = pendingAttempt?.conversationId === conversationId && pendingAttempt.question === submitted
    ? pendingAttempt : { conversationId, question: submitted, key: crypto.randomUUID() }
  pendingAttempt = attempt
  question.value = ''
  followLatest.value = true
  streaming.value = true
  activeMessageId.value = undefined
  stopping.value = false
  evidencePanel.value = undefined
  reveal.reset()
  streamedEvidence.value = []
  errorMessage.value = ''
  let terminal = false
  let failed = false
  let assistantId: string | undefined
  let completedMessage: ConversationMessage | undefined
  const localId = `local-${attempt.key}`
  if (!messages.value.some(item => item.id === localId)) messages.value.push({
    id: localId, conversationId, sequenceNo: messages.value.length + 1,
    role: 'USER', content: submitted, status: 'COMPLETED', evidence: [],
    createdAt: new Date().toISOString(),
  })
  const activeController = new AbortController()
  controller = activeController
  try {
    await conversationApi.streamMessage(projectId, conversationId, submitted, attempt.key, {
      onMessage: value => { assistantId = value.messageId; if (current(epoch)) activeMessageId.value = value.messageId },
      onEvidence: value => { if (current(epoch)) streamedEvidence.value = value },
      onToken: value => { if (current(epoch)) reveal.append(value.content) },
      onDone: value => { terminal = true; completedMessage = value; if (current(epoch)) { pendingAttempt = undefined; activeMessageId.value = undefined } },
      onFailed: value => {
        terminal = true; failed = true
        assistantId = value.messageId
        if (current(epoch)) { errorMessage.value = value.message; question.value = submitted }
      },
    }, activeController.signal)
    if (!current(epoch)) return
    const result = await conversationApi.messages(projectId, conversationId)
    if (!current(epoch)) return
    const persisted = result.find(item => item.id === assistantId)
    if (persisted && persisted.status !== 'RUNNING') {
      terminal = true
      failed = persisted.status === 'FAILED'
      pendingAttempt = undefined
      if (failed) {
        errorMessage.value = persisted.errorMessage || '回答失败，可以修改问题后重新发送。'
        question.value = submitted
      } else question.value = ''
    }
    if (!terminal) {
      errorMessage.value = '连接已结束，请刷新历史确认回答状态；原问题已保留，再次发送同一问题会复用本次请求。'
      question.value = submitted
    }
    await reveal.finish(persisted && ['COMPLETED', 'CANCELLED'].includes(persisted.status) ? persisted.content : completedMessage?.content)
    if (!current(epoch)) return
    const list = await conversationApi.list(projectId)
    if (!current(epoch)) return
    messages.value = result
    conversations.value = list
    selected.value = list.find(item => item.id === conversationId) ?? selected.value
  } catch (error) {
    if (!current(epoch)) return
    await reveal.finish(completedMessage?.content)
    if (!current(epoch)) return
    if (completedMessage?.id) {
      messages.value = [...messages.value.filter(item => item.id !== completedMessage?.id), completedMessage]
    } else messages.value = messages.value.filter(item => item.id !== localId)
    if (!terminal || failed) question.value = submitted
    errorMessage.value = readableError(error) + '。可先刷新历史查看已保存的回答。'
  } finally {
    if (current(epoch)) {
      streaming.value = false
      reveal.reset()
      streamedEvidence.value = []
      controller = undefined
      activeMessageId.value = undefined
      stopping.value = false
    }
  }
}

const workspace = ref<HTMLElement>()
let previousFocus: HTMLElement | null = null
let previousOverflow = ''
watch(() => props.open, async open => {
  if (open) {
    previousFocus = document.activeElement as HTMLElement | null
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick(); composer.value?.focus()
  } else { document.body.style.overflow = previousOverflow; previousFocus?.focus() }
}, { immediate: true })
function onWorkspaceKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (management.value) { if (!managing.value) management.value = undefined }
    else if (evidencePanel.value) evidencePanel.value = undefined
    else close()
  }
  if (event.key === 'Tab') {
    const root = management.value ? workspace.value?.querySelector('.chat-management') : workspace.value
    const items = [...(root?.querySelectorAll<HTMLElement>('button:not(:disabled), input, textarea:not(:disabled), summary, a[href], [tabindex="0"]') || [])].filter(el => !el.closest('[inert]') && el.getAttribute('aria-hidden') !== 'true')
    const first = items[0], last = items.at(-1)
    if (event.shiftKey && (document.activeElement === first || !root?.contains(document.activeElement))) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }
}
onBeforeUnmount(() => { document.body.style.overflow = previousOverflow })
watch(management, async value => { await nextTick(); if (value) workspace.value?.querySelector<HTMLInputElement>('.chat-management input, .chat-management button')?.focus(); else composer.value?.focus() })
function close() { clearTimeout(historyPoll); dialogEpoch += 1; controller?.abort(); reveal.reset(); emit('close') }
onBeforeUnmount(() => { clearTimeout(historyPoll); dialogEpoch += 1; controller?.abort(); reveal.reset() })
watch(() => [props.open, props.projectId] as const, ([open]) => {
  dialogEpoch += 1
  controller?.abort()
  controller = undefined
  selected.value = undefined
  messages.value = []
  conversations.value = []
  errorMessage.value = ''
  streaming.value = false
  creating.value = false
  loading.value = false
  pendingAttempt = undefined
  reveal.reset()
  management.value = undefined
  managing.value = false
  evidencePanel.value = undefined
  sidebarOpen.value = false
  activeMessageId.value = undefined
  stopping.value = false
  historySearch.value = ''
  followLatest.value = true
  question.value = open ? props.initialQuestion ?? '' : ''
  providers.value = []
  chosenModel.value = ''
  if (open) { void loadConversations(); void loadModels() }
}, { immediate: true })
</script>

<template>
  <div v-if="open" ref="workspace" class="modal-backdrop chat-workspace-backdrop" @keydown="onWorkspaceKey">
    <section class="modal agent-chat-modal" role="dialog" aria-modal="true" aria-labelledby="agent-chat-title">
      <header class="chat-workspace-header" :inert="!!management">
        <button class="icon-button history-toggle" type="button" aria-label="展开或收起历史对话" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen">☰</button>
        <div class="chat-wordmark"><span class="chat-brand-mark" aria-hidden="true">🌱</span><div><b>DEVMATE <span>项目助手</span></b><h2 id="agent-chat-title">{{ projectName || '项目对话' }}</h2></div></div>
        <span class="chat-framework"><i></i> Spring AI</span>
        <label v-if="modelOptions.length" class="chat-model-picker"><span class="sr-only">对话模型，切换后新建对话</span><select :value="selected ? `${selected.provider}/${selected.modelName}` : chosenModel" aria-label="对话模型，切换后新建对话" :disabled="busy || loading" @change="chooseModel"><option v-if="selected && !modelOptions.some(item => item.value === `${selected!.provider}/${selected!.modelName}`)" :value="`${selected.provider}/${selected.modelName}`">{{ selected.modelName }}（历史模型）</option><option v-for="item in modelOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><button class="chat-model-button" type="button" :disabled="busy" @click="emit('configureModel')">{{ modelOptions.length ? '模型设置' : selected?.modelName || '选择 / 配置模型' }} ⚙</button>
        <button type="button" class="button secondary chat-return" aria-label="关闭" @click="close">返回项目 ↗</button>
      </header>
      <div class="agent-chat-layout" :inert="!!management" :class="{ 'history-open': sidebarOpen, 'with-evidence': !!evidencePanel }">
        <ConversationHistory :items="conversations" :selected-id="selected?.id" v-model:search="historySearch" :busy="busy" :loading="loading" @select="selectConversation" @create="startNewChat" @rename="openManagement('rename', $event)" @delete="openManagement('delete', $event)" />
        <div class="agent-chat-main">
          <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
          <div class="conversation-toolbar"><div><b>{{ selected?.title || '新对话' }}</b><small v-if="selected">代码版本 {{ selected.revision.slice(0, 8) }} · 仅检索当前项目</small><small v-else>从业务问题出发，沿代码证据深入理解</small></div><button v-if="selected" class="button secondary refresh-chat" type="button" :disabled="loading || streaming" @click="selectConversation(selected)">刷新历史</button></div>
          <div ref="messageList" class="agent-message-list" :aria-busy="streaming || loading" @scroll="onScroll">
            <p v-if="loading" class="chat-loading" role="status">正在读取聊天记录…</p>
            <div v-if="!messages.length && !streaming && !loading" class="agent-chat-empty"><span class="chat-empty-icon" aria-hidden="true">🌼</span><small>读懂代码，从一个问题开始</small><b>一起把 {{ projectName || '这个项目' }} 讲明白</b><p>不用先读完整个仓库。告诉我你想了解什么，<br />我会结合项目源码，解释业务逻辑并给出依据。</p><div class="chat-suggestions"><button v-for="(item, index) in suggestions" :key="item" type="button" @click="fillQuestion(item)"><span>0{{ index + 1 }}</span>{{ item }}<span>↗</span></button></div><small>点选只填入问题，发送后才调用模型。</small></div>
            <article v-for="(message, index) in messages" :key="message.id" :class="message.role.toLowerCase()">
              <header><b><span class="chat-avatar">{{ message.role === 'USER' ? '你' : '🌱' }}</span>{{ message.role === 'USER' ? '你' : 'DevMate Agent' }}</b><span>{{ message.role === 'USER' ? `第 ${turnNumber(index)} 轮` : statusLabels[message.status] }}<time v-if="message.createdAt"> · {{ formatTime(message.createdAt) }}</time></span></header>
              <p v-if="message.role === 'USER'" class="chat-user-text">{{ message.content }}</p>
              <ModelAnswer v-else :content="message.content || message.errorMessage || (message.status === 'RUNNING' ? '回答仍在后台生成，可刷新历史或停止生成。' : message.status === 'CANCELLED' ? '已停止，本次尚未生成内容。' : '')" />
              <footer v-if="message.role === 'ASSISTANT'" class="chat-message-actions"><button v-if="message.content" type="button" @click="copyAnswer(message)">{{ copiedMessage === message.id ? '已复制' : '复制回答' }}</button><button v-if="message.evidence.length" type="button" class="evidence-toggle" @click="evidencePanel = { evidence: message.evidence, title: messages[index - 1]?.content || '本轮回答' }">查看 {{ message.evidence.length }} 条代码证据 ↗</button><span v-if="message.latencyMs">{{ (message.latencyMs / 1000).toFixed(1) }} 秒 · {{ message.modelName }}</span><button v-if="message.status === 'RUNNING' && !streaming" type="button" :disabled="stopping" @click="stopAnswer">{{ stopping ? '正在停止…' : '停止生成' }}</button></footer>
              <p v-if="message.status === 'CANCELLED' && message.content" class="chat-stopped-note">已停止生成，以上内容尚未完整回答。</p>
            </article>
            <article v-if="streaming" class="assistant streaming" aria-label="正在生成的回答"><header><b><span class="chat-avatar" aria-hidden="true">🌱</span>DevMate Agent</b><span role="status">{{ stopping ? '正在停止并保存…' : streamedText ? '正在逐字输出' : streamedEvidence.length ? '已找到代码，正在组织回答…' : '正在查找项目代码…' }}</span></header><ModelAnswer v-if="streamedText" :content="streamedText" /><p v-else class="chat-thinking">回答将逐步显示<span class="stream-cursor" aria-hidden="true"></span></p><div class="chat-message-actions"><button v-if="streamedEvidence.length" type="button" @click="evidencePanel = { evidence: streamedEvidence, title: '当前回答的代码依据' }">已找到 {{ streamedEvidence.length }} 条代码证据 ↗</button></div></article>
          </div>
          <button v-if="!followLatest" type="button" class="button secondary chat-latest" @click="scrollLatest(true)">↓ 回到最新消息</button>
          <form class="agent-chat-input" @submit.prevent="sendQuestion"><div class="chat-composer-box"><label class="sr-only" for="project-chat-question">向当前项目提问</label><textarea ref="composer" id="project-chat-question" v-model="question" rows="1" maxlength="4000" placeholder="给 DevMate 发送消息，聊聊这个项目…" :disabled="busy" @compositionstart="isComposing = true" @compositionend="isComposing = false" @keydown="onComposerKey"></textarea><button v-if="streaming" type="button" class="chat-send stop-answer" :disabled="stopping || !activeMessageId" :aria-label="stopping ? '正在停止' : '停止生成'" @click="stopAnswer">■</button><button v-else class="chat-send" aria-label="发送问题" :disabled="busy || loading || !question.trim()">↑</button></div><div class="chat-composer-note"><small>{{ streaming ? '停止会保存已有内容；返回项目后后台仍会完成回答。' : 'Enter 发送 · Shift + Enter 换行' }}</small><small>{{ question.length }}/4000 · 自动保存</small></div><p class="chat-disclaimer">AI 回答仅供参考，请结合代码证据核对。<span class="sr-only" role="status">{{ copyNotice }}</span></p></form>
        </div>
        <ChatEvidencePanel v-if="evidencePanel" :evidence="evidencePanel.evidence" :title="evidencePanel.title" @close="evidencePanel = undefined" />
      </div>
      <div v-if="management" class="chat-management-backdrop"><form class="chat-management" role="alertdialog" aria-modal="true" aria-labelledby="chat-management-title" @submit.prevent="manageConversation"><h3 id="chat-management-title">{{ management.kind === 'rename' ? '重命名对话' : '删除这段对话？' }}</h3><label v-if="management.kind === 'rename'">对话标题<input v-model="titleDraft" maxlength="100" required :disabled="managing" /></label><p v-else>“{{ management.item.title }}”将从你的历史列表移除。其他对话、项目和模型配置不受影响。</p><p v-if="managementError" role="alert" class="notice error">{{ managementError }}</p><footer><button type="button" class="button secondary" :disabled="managing" @click="management = undefined">取消</button><button class="button" :class="management.kind === 'delete' ? 'danger' : 'primary'" :disabled="managing || (management.kind === 'rename' && !titleDraft.trim())">{{ managing ? '保存中…' : management.kind === 'rename' ? '保存标题' : '确认删除' }}</button></footer></form></div>
    </section>
  </div>
</template>
