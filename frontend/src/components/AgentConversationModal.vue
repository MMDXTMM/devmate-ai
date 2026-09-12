<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError } from '../services/apiClient'
import { conversationApi } from '../services/conversationApi'
import type { AgentConversation, ConversationEvidence, ConversationMessage } from '../types/conversation'

const props = defineProps<{
  open: boolean
  projectId?: string
  projectName?: string
}>()
const emit = defineEmits<{ close: [] }>()

const conversations = ref<AgentConversation[]>([])
const selected = ref<AgentConversation>()
const messages = ref<ConversationMessage[]>([])
const question = ref('')
const loading = ref(false)
const creating = ref(false)
const streaming = ref(false)
const streamedText = ref('')
const streamedEvidence = ref<ConversationEvidence[]>([])
const errorMessage = ref('')
let controller: AbortController | undefined

async function loadConversations() {
  if (!props.projectId) return
  loading.value = true
  errorMessage.value = ''
  try {
    conversations.value = await conversationApi.list(props.projectId)
    if (!selected.value && conversations.value.length) await selectConversation(conversations.value[0])
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    loading.value = false
  }
}

async function createConversation() {
  if (!props.projectId) return
  creating.value = true
  errorMessage.value = ''
  try {
    const created = await conversationApi.create(props.projectId)
    conversations.value = [created, ...conversations.value]
    selected.value = created
    messages.value = []
  } catch (error) {
    errorMessage.value = readableError(error)
  } finally {
    creating.value = false
  }
}

async function selectConversation(conversation: AgentConversation) {
  if (!props.projectId || streaming.value) return
  selected.value = conversation
  errorMessage.value = ''
  try {
    messages.value = await conversationApi.messages(props.projectId, conversation.id)
  } catch (error) {
    errorMessage.value = readableError(error)
  }
}

async function sendQuestion() {
  if (!props.projectId || !question.value.trim() || streaming.value) return
  if (!selected.value) {
    await createConversation()
    if (!selected.value) return
  }
  const submitted = question.value.trim()
  const conversationId = selected.value.id
  question.value = ''
  streaming.value = true
  streamedText.value = ''
  streamedEvidence.value = []
  errorMessage.value = ''
  const localId = `local-${Date.now()}`
  messages.value.push({
    id: localId,
    conversationId,
    sequenceNo: messages.value.length + 1,
    role: 'USER', content: submitted, status: 'COMPLETED', evidence: [],
    createdAt: new Date().toISOString(),
  })
  controller = new AbortController()
  try {
    await conversationApi.streamMessage(
      props.projectId, conversationId, submitted, crypto.randomUUID(),
      {
        onEvidence: (value) => { streamedEvidence.value = value },
        onToken: (value) => { streamedText.value += value.content },
        onDone: () => undefined,
        onFailed: (value) => { errorMessage.value = value.message },
      },
      controller.signal,
    )
    if (props.open) {
      messages.value = await conversationApi.messages(props.projectId, conversationId)
      conversations.value = await conversationApi.list(props.projectId)
      selected.value = conversations.value.find((item) => item.id === conversationId) ?? selected.value
    }
  } catch (error) {
    messages.value = messages.value.filter((item) => item.id !== localId)
    errorMessage.value = readableError(error)
  } finally {
    streaming.value = false
    streamedText.value = ''
    streamedEvidence.value = []
    controller = undefined
  }
}

function close() {
  controller?.abort()
  emit('close')
}

function readableError(error: unknown) {
  return error instanceof ApiError ? error.message : '项目对话操作失败，请稍后重试'
}

watch(() => props.open, (open) => {
  if (open) void loadConversations()
  else {
    controller?.abort()
    selected.value = undefined
    messages.value = []
    conversations.value = []
    errorMessage.value = ''
  }
})
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <section class="modal agent-chat-modal" role="dialog" aria-modal="true" aria-labelledby="agent-chat-title">
      <header class="modal-header">
        <div><small>JAVA PROJECT AGENT</small><h2 id="agent-chat-title">{{ projectName }} · 项目对话</h2></div>
        <button type="button" aria-label="关闭" @click="close">×</button>
      </header>
      <div class="agent-chat-layout">
        <aside class="conversation-list">
          <button class="button primary" type="button" :disabled="creating" @click="createConversation">
            {{ creating ? '创建中…' : '＋ 新建对话' }}
          </button>
          <p v-if="loading">正在加载历史对话…</p>
          <button
            v-for="item in conversations" :key="item.id" type="button"
            :class="{ active: selected?.id === item.id }" @click="selectConversation(item)"
          >
            <b>{{ item.title }}</b><small>{{ item.provider }} / {{ item.modelName }}</small>
          </button>
        </aside>
        <div class="agent-chat-main">
          <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
          <div class="agent-message-list" aria-live="polite">
            <div v-if="!selected" class="agent-chat-empty">
              <b>建立一段有记忆的项目对话</b>
              <p>Agent 会保留上下文，并为每轮回答重新检索当前项目的真实代码证据。</p>
            </div>
            <article v-for="message in messages" :key="message.id" :class="message.role.toLowerCase()">
              <header><b>{{ message.role === 'USER' ? '你' : 'DevMate Agent' }}</b><span>{{ message.status }}</span></header>
              <p>{{ message.content || message.errorMessage }}</p>
              <details v-if="message.evidence.length">
                <summary>查看 {{ message.evidence.length }} 条代码证据</summary>
                <div v-for="item in message.evidence" :key="item.chunkId" class="chat-evidence">
                  <b>{{ item.filePath }}:{{ item.startLine }}-{{ item.endLine }}</b>
                  <small>{{ item.symbolName }}</small>
                  <pre><code>{{ item.excerpt }}</code></pre>
                </div>
              </details>
            </article>
            <article v-if="streaming" class="assistant streaming">
              <header><b>DevMate Agent</b><span>SSE 输出中</span></header>
              <p>{{ streamedText }}<i class="stream-cursor"></i></p>
              <small v-if="streamedEvidence.length">已检索 {{ streamedEvidence.length }} 条代码证据</small>
            </article>
          </div>
          <form class="agent-chat-input" @submit.prevent="sendQuestion">
            <textarea v-model="question" rows="3" maxlength="4000" placeholder="例如：创建订单时经过哪些 Service 和数据表？" :disabled="streaming"></textarea>
            <div><small>回答基于当前 revision 和 RAG 证据；动态运行行为仍需验证。</small><button class="button primary" :disabled="streaming || !question.trim()">{{ streaming ? '回答中…' : '发送' }}</button></div>
          </form>
        </div>
      </div>
    </section>
  </div>
</template>
