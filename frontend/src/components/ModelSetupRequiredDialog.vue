<script setup lang="ts">
import { useModalFocus } from '../composables/useModalFocus'

const props = defineProps<{ open: boolean; purpose: string }>()
const emit = defineEmits<{ close: []; configure: [] }>()
const dialogRef = useModalFocus(() => props.open, () => emit('close'))
</script>

<template>
  <div v-if="open" class="modal-backdrop model-setup-backdrop" @click.self="emit('close')">
    <section :ref="dialogRef" class="modal model-setup-dialog" role="dialog" aria-modal="true" aria-labelledby="model-setup-title" aria-describedby="model-setup-description" tabindex="-1">
      <button class="icon-button setup-close" type="button" aria-label="关闭配置提醒" @click="emit('close')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
      </button>
      <header class="setup-header">
        <span class="setup-illustration" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 6v6m8-6v6M9 12h14v3a7 7 0 0 1-14 0v-3Zm7 10v5" />
            <path class="setup-spark" d="M25 4v4m-2-2h4M5 22v4m-2-2h4" />
          </svg>
        </span>
        <p class="setup-eyebrow">让 AI 成为你的项目助手</p>
        <h2 id="model-setup-title">先连接一个大模型</h2>
      </header>
      <div class="setup-content">
        <p id="model-setup-description"><strong>{{ purpose }}</strong>需要使用 AI。为当前账户保存并启用一个 API 连接后，就可以继续了。</p>
        <section class="setup-preparation" aria-labelledby="setup-preparation-title">
          <h3 id="setup-preparation-title">配置前，准备好这些</h3>
          <dl class="setup-requirements">
            <div>
              <span class="setup-item-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="9" r="4" /><path d="m11 12 8 8m-3-3 2-2m-5 2 2-2" /></svg>
              </span>
              <dt>API Key</dt>
              <dd>服务商提供的访问密钥</dd>
            </div>
            <div>
              <span class="setup-item-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="6" rx="2" /><rect x="4" y="14" width="16" height="6" rx="2" /><path d="M8 7h.01M8 17h.01m7-10h2m-2 10h2" /></svg>
              </span>
              <dt>请求地址与模型名称</dt>
              <dd>在连接中心选择或填写</dd>
            </div>
          </dl>
        </section>
        <p class="setup-note">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 11v5m0-9h.01" /></svg>
          <span>暂不配置不会发起 AI 请求，已有项目和聊天记录仍可查看。</span>
        </p>
      </div>
      <footer class="setup-actions">
        <button class="button secondary" type="button" @click="emit('close')">暂不配置</button>
        <button class="button primary" type="button" @click="emit('configure')">
          去配置 API
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5" /></svg>
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.model-setup-backdrop { z-index: 150; }
.model-setup-dialog {
  position: relative;
  width: min(500px, 100%);
  max-height: calc(100dvh - 48px);
  border-radius: 26px;
  background: var(--surface);
  box-shadow: 0 24px 90px #38234530, 0 4px 16px #3823450d;
}
.setup-close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: transparent;
}
.setup-close svg { width: 18px; height: 18px; }
.setup-header {
  padding: 32px 32px 0;
  background: radial-gradient(ellipse at top left, #efe7f680, transparent 75%);
}
.setup-illustration {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 22px;
  border: 1px solid var(--accent-line);
  border-radius: 18px;
  color: var(--accent);
  background: linear-gradient(140deg, #f6f0fb, #e9dcf4);
  box-shadow: 0 4px 0 #e7dced80, inset 0 1px 0 #fff;
}
.setup-illustration svg { width: 34px; height: 34px; }
.setup-spark { color: #a68a6a; }
.setup-eyebrow { margin: 0 0 8px; color: var(--accent); font-size: 12px; font-weight: 600; letter-spacing: .04em; }
.setup-header h2 { margin: 0; color: var(--ink); font-size: 24px; line-height: 1.4; letter-spacing: -.03em; }
.setup-content { padding: 14px 32px 24px; }
#model-setup-description { margin: 0; color: var(--ink-soft); font-size: 14px; line-height: 1.9; overflow-wrap: anywhere; }
#model-setup-description strong { color: var(--ink); font-weight: 600; }
.setup-preparation { margin-top: 24px; padding: 18px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface-soft); }
.setup-preparation h3 { margin: 0 0 16px; color: var(--ink-soft); font-size: 12px; font-weight: 600; }
.setup-requirements { display: grid; gap: 16px; margin: 0; }
.setup-requirements > div { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 3px 12px; align-items: center; }
.setup-item-icon { grid-row: span 2; display: grid; place-items: center; width: 34px; height: 34px; color: var(--accent); border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.setup-item-icon svg { width: 20px; height: 20px; }
.setup-requirements dt { color: var(--ink); font-size: 13px; font-weight: 600; }
.setup-requirements dd { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.6; }
.setup-note { display: flex; align-items: flex-start; gap: 8px; margin: 18px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.setup-note svg { flex: 0 0 16px; width: 16px; height: 16px; margin-top: 3px; }
.setup-actions { display: grid; grid-template-columns: 1fr 1.4fr; gap: 12px; padding: 20px 32px 26px; border-top: 1px solid var(--line); background: #fbf8fc; }
.setup-actions .button { display: inline-flex; align-items: center; justify-content: center; min-height: 46px; font-size: 14px; }
.setup-actions .primary { gap: 10px; }
.setup-actions svg { width: 18px; height: 18px; }
@media (max-width: 540px) {
  .model-setup-backdrop { padding: 16px; }
  .model-setup-dialog { max-height: calc(100dvh - 32px); border-radius: 22px; }
  .setup-header { padding: 26px 24px 0; }
  .setup-header h2 { font-size: 22px; }
  .setup-illustration { margin-bottom: 20px; }
  .setup-content { padding: 12px 24px 22px; }
  .setup-preparation { margin-top: 20px; padding: 16px; }
  .setup-actions { padding: 18px 24px 24px; gap: 10px; }
}
</style>
