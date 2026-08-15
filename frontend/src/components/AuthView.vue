<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ApiError } from '../services/apiClient'
import { authApi } from '../services/authApi'
import type { AuthSession } from '../types/auth'

const emit = defineEmits<{ authenticated: [session: AuthSession] }>()

const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '', email: '' })

function switchMode(nextMode: 'login' | 'register') {
  mode.value = nextMode
  errorMessage.value = ''
}

async function submit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const session = mode.value === 'login'
      ? await authApi.login({ username: form.username, password: form.password })
      : await authApi.register({
          username: form.username,
          password: form.password,
          email: form.email,
        })
    emit('authenticated', session)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '认证失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-brand">
        <span class="brand-mark">D</span>
        <span><b>DevMate</b><small>UNDERSTAND & BUILD</small></span>
      </div>

      <div class="auth-heading">
        <p class="eyebrow">PROJECT UNDERSTANDING</p>
        <h1>{{ mode === 'login' ? '继续理解项目' : '创建账号' }}</h1>
        <p>{{ mode === 'login' ? '登录后进入你的 Java 项目工作台。' : '导入项目，快速找到业务入口和开发起点。' }}</p>
      </div>

      <div class="auth-tabs" role="tablist" aria-label="认证方式">
        <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input
            v-model.trim="form.username"
            autocomplete="username"
            pattern="[A-Za-z0-9_]{3,32}"
            minlength="3"
            maxlength="32"
            required
          />
        </label>
        <label v-if="mode === 'register'">
          <span>邮箱 <small>选填</small></span>
          <input v-model.trim="form.email" type="email" autocomplete="email" maxlength="255" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            minlength="8"
            maxlength="72"
            required
          />
        </label>
        <div v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</div>
        <button class="button primary auth-submit" type="submit" :disabled="submitting">
          {{ submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </section>
  </main>
</template>
