<script setup lang="ts">
import type { ConversationEvidence } from '../types/conversation'
import AnswerCodeBlock from './AnswerCodeBlock.vue'
defineProps<{ evidence: ConversationEvidence[]; title: string }>()
defineEmits<{ close: [] }>()
</script>
<template>
  <aside class="chat-evidence-panel" aria-label="本轮代码证据">
    <header><div><small>回答依据</small><h3>代码证据 · {{ evidence.length }}</h3></div><button type="button" class="icon-button" aria-label="收起代码证据" @click="$emit('close')">×</button></header>
    <p class="evidence-question">{{ title }}</p>
    <div class="evidence-scroll">
      <article v-for="(item, index) in evidence" :key="item.chunkId" class="chat-evidence"><span class="evidence-number">证据 {{ index + 1 }}</span><b>{{ item.filePath }}</b><small>{{ item.symbolName }} · 第 {{ item.startLine }}–{{ item.endLine }} 行</small><AnswerCodeBlock :code="item.excerpt" language="java" /></article>
      <p v-if="!evidence.length">本轮尚未找到可展示的代码证据。模型结论仍需核对。</p>
    </div>
  </aside>
</template>
