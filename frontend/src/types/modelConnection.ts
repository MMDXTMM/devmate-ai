export interface ModelProvider {
  provider: 'DEEPSEEK' | 'DASHSCOPE' | 'OPENAI'
  displayName: string
  baseUrl: string
  models: string[]
  configured: boolean
  active: boolean
  selectedModel: string
}

export interface ModelConnectionTest {
  provider: string
  model: string
  latencyMs: number
  message: string
}
