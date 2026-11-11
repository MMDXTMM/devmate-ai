<script setup lang="ts">
import { computed } from 'vue'
import type { AgentConversation } from '../types/conversation'
const props = defineProps<{ items: AgentConversation[]; selectedId?: string; search: string; busy: boolean; loading: boolean }>()
const emit = defineEmits<{ select: [item: AgentConversation]; create: []; rename: [item: AgentConversation]; delete: [item: AgentConversation]; 'update:search': [value: string] }>()
const groups = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const buckets: Record<string, AgentConversation[]> = { 今天: [], 昨天: [], '最近七天': [], 更早: [] }
  for (const item of props.items.filter(item => item.title.toLowerCase().includes(props.search.trim().toLowerCase()))) {
    const time = new Date(item.updatedAt).getTime()
    const label = time >= today ? '今天' : time >= today - 86400000 ? '昨天' : time >= today - 7 * 86400000 ? '最近七天' : '更早'
    buckets[label]!.push(item)
  }
  return Object.entries(buckets).filter(([, items]) => items.length)
})
</script>
<template>
  <aside class="conversation-list" aria-label="当前项目的历史对话">
    <button class="button primary new-conversation" type="button" :disabled="busy || loading" @click="emit('create')"><span>＋</span> 新建对话</button>
    <label class="conversation-search"><span>历史对话 · {{ items.length }}</span><input :value="search" type="search" aria-label="搜索历史对话" placeholder="搜索聊天标题" @input="emit('update:search', ($event.target as HTMLInputElement).value)" /></label>
    <div class="history-items">
      <p v-if="loading">正在读取已保存记录…</p>
      <p v-else-if="!items.length">发送第一个问题，开始理解项目。</p>
      <p v-else-if="!groups.length">没有找到相关对话。</p>
      <section v-for="[label, conversations] in groups" :key="label" class="history-group">
        <h3>{{ label }}</h3>
        <div v-for="item in conversations" :key="item.id" class="conversation-item" :class="{ active: selectedId === item.id }">
          <button class="conversation-select" type="button" :disabled="busy" :aria-current="selectedId === item.id ? 'true' : undefined" @click="emit('select', item)"><b>{{ item.title }}</b><small>{{ item.modelName }}</small></button>
          <details class="conversation-menu">
            <summary :aria-label="`管理对话：${item.title}`">···</summary>
            <div><button type="button" :disabled="busy" @click="emit('rename', item); ($event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open')">重命名</button><button type="button" :disabled="busy" @click="emit('delete', item); ($event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open')">删除对话</button></div>
          </details>
        </div>
      </section>
    </div>
    <p class="history-save-note">聊天保存在当前账号<br />仅展示当前项目最近 50 段对话</p>
  </aside>
</template>
