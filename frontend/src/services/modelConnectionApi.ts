import { apiRequest } from './apiClient'
import type { ModelConnectionTest, ModelProvider } from '../types/modelConnection'

export const modelConnectionApi = {
  list: () => apiRequest<ModelProvider[]>('/api/model-connections'),
  update: (provider: string, model: string, apiKey: string, settings?: { baseUrl: string; protocol: 'CHAT_COMPLETIONS' }) => apiRequest<ModelProvider[]>('/api/model-connections', {
    method: 'PUT', body: JSON.stringify({ provider, model, apiKey, ...settings }),
  }),
  remove: (provider: string) => apiRequest<ModelProvider[]>(`/api/model-connections/${encodeURIComponent(provider)}`, { method: 'DELETE' }),
  test: () => apiRequest<ModelConnectionTest>('/api/model-connections/test', { method: 'POST' }),
}
