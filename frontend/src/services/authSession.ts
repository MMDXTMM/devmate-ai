import type { AuthSession } from '../types/auth'

const SESSION_KEY = 'devmate.auth.session'
const listeners = new Set<() => void>()

export function getAuthSession(): AuthSession | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as AuthSession
    if (!session.accessToken || !session.user?.id || new Date(session.expiresAt).getTime() <= Date.now()) {
      clearAuthSession()
      return null
    }
    return session
  } catch {
    clearAuthSession()
    return null
  }
}

export function setAuthSession(session: AuthSession) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  notify()
}

export function clearAuthSession() {
  sessionStorage.removeItem(SESSION_KEY)
  notify()
}

export function subscribeAuthSession(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function notify() {
  listeners.forEach((listener) => listener())
}
