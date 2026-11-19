<script setup lang="ts">
import type { ConversationContext } from '../types/conversation'
defineProps<{ context: ConversationContext }>()
const tools: Record<string, string> = { search_code: '搜索相关实现', read_code: '读取代码', related_code: '查询直接调用关系' }
const statuses: Record<string, string> = { SUCCEEDED: '已完成', REJECTED: '未能读取', REPEATED: '已跳过重复请求' }
</script>
<template>
  <details class="conversation-context-status">
    <summary>取证过程与覆盖范围<span v-if="context.actions.length"> · {{ context.actions.length }} 次工具请求</span></summary>
    <p v-if="context.memoryUsed">已带入较早的用户问题主题，作为讨论线索；事实仍以本轮代码证据为准。</p>
    <ol v-if="context.actions.length"><li v-for="(action, index) in context.actions" :key="index">{{ tools[action.tool] ?? '只读取证' }}：{{ statuses[action.status] ?? '未完成' }} · 当前 {{ action.evidenceCount }} 条证据</li></ol>
    <ul v-if="context.warnings.length" aria-label="上下文覆盖提示"><li v-for="warning in context.warnings" :key="warning">{{ warning }}</li></ul>
    <p v-else>本轮未触发覆盖上限提示。静态代码阅读仍不等于运行验证。</p>
  </details>
</template>
<style scoped>
.conversation-context-status { margin: .75rem 0; border: 1px solid #dce6e3; border-radius: 12px; padding: .65rem .85rem; background: #f6faf8; font-size: .85rem; color: #425b53; }
summary { cursor: pointer; font-weight: 600; }
li { margin: .35rem 0; overflow-wrap: anywhere; }
</style>
