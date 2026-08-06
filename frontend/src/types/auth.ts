export interface AuthUser {
  id: string
  username: string
  email?: string
}

export interface AuthSession {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: AuthUser
}

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm extends LoginForm {
  email?: string
}
