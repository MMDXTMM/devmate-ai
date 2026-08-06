import type { AuthSession, AuthUser, LoginForm, RegisterForm } from '../types/auth'
import { apiRequest } from './apiClient'

export const authApi = {
  register(form: RegisterForm): Promise<AuthSession> {
    return apiRequest('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ ...form, email: form.email?.trim() || null }),
    })
  },

  login(form: LoginForm): Promise<AuthSession> {
    return apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  me(): Promise<AuthUser> {
    return apiRequest('/api/auth/me')
  },
}
