export interface ModelProvider {
  provider: 'DEEPSEEK' | 'DASHSCOPE' | 'OPENAI' | 'COMPATIBLE'
  displayName: string
  baseUrl: string
  models: string[]
  configured: boolean
  active: boolean
  selectedModel: string
  protocol?: 'CHAT_COMPLETIONS'
  allowedBaseUrls?: string[]
}

export interface ModelConnectionTest {
  provider: string
  model: string
  latencyMs: number
  message: string
}
