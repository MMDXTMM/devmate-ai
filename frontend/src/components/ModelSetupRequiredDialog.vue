<script setup lang="ts">
defineProps<{ open: boolean; purpose: string }>()
const emit = defineEmits<{ close: []; configure: [] }>()
</script>

<template>
  <div v-if="open" class="modal-backdrop model-setup-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section class="modal model-setup-dialog" role="dialog" aria-modal="true" aria-labelledby="model-setup-title" aria-describedby="model-setup-description">
      <header class="modal-header">
        <span class="surface-icon" aria-hidden="true">🔌</span>
        <h2 id="model-setup-title">先连接一个大模型</h2>
        <button class="icon-button" type="button" aria-label="关闭配置提醒" @click="emit('close')">×</button>
      </header>
      <p id="model-setup-description">{{ purpose }}需要先为当前账户保存并启用 API 连接，AI 才能继续为你分析项目。</p>
      <p>准备好 API Key、请求地址和模型名称，前往连接中心完成配置并测试。</p>
      <p class="setup-note">取消不会创建项目或发送 AI 请求。已有项目、源码和聊天记录仍可查看。</p>
      <footer><button class="button secondary" type="button" @click="emit('close')">暂不配置</button><button class="button primary" type="button" autofocus @click="emit('configure')">去配置 API →</button></footer>
    </section>
  </div>
</template>
<style scoped>
.model-setup-backdrop { z-index: 150; }
.model-setup-dialog { max-width: 520px; }
.model-setup-dialog p { line-height: 1.8; }
.setup-note { font-size: 13px; color: #746b80; }
footer { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
