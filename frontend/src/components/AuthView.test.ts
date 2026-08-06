import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AuthView from './AuthView.vue'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('AuthView', () => {
  it('logs in and emits the authenticated session', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        accessToken: 'jwt-token',
        tokenType: 'Bearer',
        expiresAt: '2099-01-01T00:00:00Z',
        user: { id: '1', username: 'alice' },
      },
    }))
    const wrapper = mount(AuthView)

    await wrapper.get('input[autocomplete="username"]').setValue('alice')
    await wrapper.get('input[type="password"]').setValue('Password123')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.emitted('authenticated')).toHaveLength(1))

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('switches to registration and displays backend errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 40900,
      message: '用户名或邮箱已存在',
    }, 409))
    const wrapper = mount(AuthView)

    await wrapper.get('.auth-tabs button:nth-child(2)').trigger('click')
    await wrapper.get('input[autocomplete="username"]').setValue('alice')
    await wrapper.get('input[type="email"]').setValue('alice@example.com')
    await wrapper.get('input[type="password"]').setValue('Password123')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('用户名或邮箱已存在'))
  })
})
