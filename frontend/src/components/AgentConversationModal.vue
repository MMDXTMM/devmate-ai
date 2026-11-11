<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useStreamingText } from '../composables/useStreamingText'
import { ApiError } from '../services/apiClient'
import { conversationApi } from '../services/conversationApi'
import ModelAnswer from './ModelAnswer.vue'
import type { AgentConversation, ConversationEvidence, ConversationMessage } from '../types/conversation'

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
const filteredConversations = computed(() => conversations.value.filter(item => item.title.toLowerCase().includes(historySearch.value.trim().toLowerCase())))
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
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void sendQuestion() }
}
watch(streamedText, () => { void scrollLatest() })
watch(() => messages.value.length, () => { void scrollLatest() })
const streamedEvidence = ref<ConversationEvidence[]>([])
const errorMessage = ref('')
let controller: AbortController | undefined
let dialogEpoch = 0
let selectionEpoch = 0
let pendingAttempt: { conversationId: string; question: string; key: string } | undefined
const statusLabels = { RUNNING: '回答中', COMPLETED: '已完成', FAILED: '回答失败' }
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
  if (!props.projectId || creating.value || streaming.value) return
  const epoch = dialogEpoch
  selectionEpoch += 1
  creating.value = true
  errorMessage.value = ''
  try {
    const created = await conversationApi.create(props.projectId)
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
  if (!props.projectId || streaming.value || creating.value) return
  const epoch = dialogEpoch
  const selection = ++selectionEpoch
  selected.value = conversation
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
  if (!props.projectId || !question.value.trim() || streaming.value || creating.value || loading.value) return
  const epoch = dialogEpoch
  const projectId = props.projectId
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
      onMessage: value => { assistantId = value.messageId },
      onEvidence: value => { if (current(epoch)) streamedEvidence.value = value },
      onToken: value => { if (current(epoch)) reveal.append(value.content) },
      onDone: value => { terminal = true; completedMessage = value; if (current(epoch)) pendingAttempt = undefined },
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
    await reveal.finish(persisted?.status === 'COMPLETED' ? persisted.content : completedMessage?.content)
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
    }
  }
}

function close() { dialogEpoch += 1; controller?.abort(); reveal.reset(); emit('close') }
onBeforeUnmount(() => { dialogEpoch += 1; controller?.abort(); reveal.reset() })
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
  historySearch.value = ''
  followLatest.value = true
  question.value = open ? props.initialQuestion ?? '' : ''
  if (open) void loadConversations()
}, { immediate: true })
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <section class="modal agent-chat-modal" role="dialog" aria-modal="true" aria-labelledby="agent-chat-title">
      <header class="modal-header">
        <div><small>项目专属 AI 助手</small><h2 id="agent-chat-title">{{ projectName }} · 项目对话</h2><p>围绕业务、接口和实现持续追问，聊天记录自动保存到当前账号。</p></div>
        <button class="button secondary" type="button" :disabled="streaming" @click="emit('configureModel')">模型连接</button>
        <button type="button" class="icon-button" aria-label="关闭" @click="close">×</button>
      </header>
      <div class="agent-chat-layout">
        <aside class="conversation-list" aria-label="当前项目的历史对话">
          <button class="button primary" type="button" :disabled="creating || streaming || loading" @click="createConversation">{{ creating ? '创建中…' : '＋ 新建对话' }}</button>
          <label class="conversation-search"><span>历史对话 · {{ conversations.length }}</span><input v-model="historySearch" type="search" aria-label="搜索历史对话" placeholder="搜索聊天标题" /></label>
          <p v-if="loading">正在读取已保存记录…</p>
          <p v-else-if="!conversations.length">还没有聊天，发送第一个问题即可开始。</p>
          <p v-else-if="!filteredConversations.length">没有找到相关对话。</p>
          <button v-for="item in filteredConversations" :key="item.id" type="button" :disabled="streaming || creating" :aria-current="selected?.id === item.id ? 'true' : undefined" :class="{ active: selected?.id === item.id }" @click="selectConversation(item)">
            <b>{{ item.title }}</b><small>{{ formatTime(item.updatedAt) }}</small><small>{{ item.modelName }}</small>
          </button>
          <p class="history-save-note">记录保存在服务器，刷新或关闭后仍可查看。仅显示当前项目、当前账号的最近 50 段对话。</p>
        </aside>
        <div class="agent-chat-main">
          <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
          <div v-if="selected" class="conversation-toolbar">
            <div><b>{{ selected.title }}</b><small>{{ selected.modelName }} · 代码版本 {{ selected.revision.slice(0, 8) }}</small></div>
            <button class="button secondary refresh-chat" type="button" :disabled="loading || streaming" @click="selectConversation(selected)">刷新历史</button>
          </div>
          <div ref="messageList" class="agent-message-list" :aria-busy="streaming || loading" @scroll="onScroll">
            <div v-if="!messages.length && !streaming && !loading" class="agent-chat-empty">
              <span class="chat-empty-icon" aria-hidden="true">AI</span><b>把这个项目讲明白</b>
              <p>可以从整体功能问起，再追问某个业务怎么实现。每轮回答会查找项目代码，并附上可核对的证据。</p>
              <div class="chat-suggestions"><button v-for="item in suggestions" :key="item" type="button" @click="question = item">{{ item }}</button></div>
              <small>点选问题只填入输入框，发送后才调用模型。</small>
            </div>
            <article v-for="message in messages" :key="message.id" :class="message.role.toLowerCase()">
              <header><b>{{ message.role === 'USER' ? '你' : 'DevMate Agent' }}</b><span>{{ message.id.startsWith('local-') ? '已发送' : statusLabels[message.status] }}<time v-if="message.createdAt"> · {{ formatTime(message.createdAt) }}</time></span></header>
              <ModelAnswer :content="message.content || message.errorMessage || (message.status === 'RUNNING' ? '回答仍在后台生成，稍后刷新历史即可查看。' : '')" />
              <details v-if="message.evidence.length"><summary>查看 {{ message.evidence.length }} 条代码证据</summary>
                <div v-for="(item, index) in message.evidence" :key="item.chunkId" class="chat-evidence"><b>[证据{{ index + 1 }}] {{ item.filePath }}:{{ item.startLine }}-{{ item.endLine }}</b><small>{{ item.symbolName }}</small><pre><code>{{ item.excerpt }}</code></pre></div>
              </details>
            </article>
            <article v-if="streaming" class="assistant streaming" aria-label="正在生成的回答">
              <header><b>DevMate Agent</b><span role="status">{{ streamedText ? '正在逐字输出' : streamedEvidence.length ? '已找到代码，正在组织回答…' : '正在查找项目代码…' }}</span></header>
              <ModelAnswer v-if="streamedText" :content="streamedText" /><p v-else class="chat-thinking">回答将逐步显示，无需等待全部生成。<span class="stream-cursor" aria-hidden="true"></span></p>
              <small v-if="streamedEvidence.length">本轮已找到 {{ streamedEvidence.length }} 条代码证据</small>
            </article>
          </div>
          <button v-if="!followLatest" type="button" class="button secondary chat-latest" @click="scrollLatest(true)">↓ 回到最新消息</button>
          <form class="agent-chat-input" @submit.prevent="sendQuestion">
            <label class="sr-only" for="project-chat-question">向当前项目提问</label>
            <textarea id="project-chat-question" v-model="question" rows="3" maxlength="4000" placeholder="例如：这个项目有哪些功能？选一条核心流程讲给我听。" :disabled="streaming || creating" @keydown="onComposerKey"></textarea>
            <div><small>{{ streaming ? '关闭窗口后后台仍会完成回答，下次打开可查看。' : 'Enter 发送 · Shift + Enter 换行 · 聊天自动保存' }}</small><button class="button primary" :disabled="streaming || creating || loading || !question.trim()">{{ streaming ? '回答中…' : '发送问题 ↑' }}</button></div>
          </form>
        </div>
      </div>
    </section>
  </div>
</template>
