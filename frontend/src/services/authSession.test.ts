import { describe, expect, it, vi } from 'vitest'
import { clearAuthSession, getAuthSession, setAuthSession, subscribeAuthSession } from './authSession'

const session = {
  accessToken: 'jwt-token',
  tokenType: 'Bearer' as const,
  expiresAt: '2099-01-01T00:00:00Z',
  user: { id: '2084116785588305922', username: 'alice' },
}

describe('authSession', () => {
  it('stores the token only for the browser session and notifies subscribers', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeAuthSession(listener)

    setAuthSession(session)

    expect(getAuthSession()).toEqual(session)
    expect(sessionStorage.getItem('devmate.auth.session')).toContain('jwt-token')
    expect(listener).toHaveBeenCalledOnce()
    unsubscribe()
  })

  it('removes expired or malformed sessions', () => {
    sessionStorage.setItem('devmate.auth.session', JSON.stringify({
      ...session,
      expiresAt: '2020-01-01T00:00:00Z',
    }))
    expect(getAuthSession()).toBeNull()

    sessionStorage.setItem('devmate.auth.session', '{invalid')
    expect(getAuthSession()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('clears an active session', () => {
    setAuthSession(session)
    clearAuthSession()
    expect(getAuthSession()).toBeNull()
  })
})
