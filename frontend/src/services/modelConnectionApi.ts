import { apiRequest } from './apiClient'
import type { ModelConnectionTest, ModelProvider } from '../types/modelConnection'

export const modelConnectionApi = {
  list: () => apiRequest<ModelProvider[]>('/api/model-connections'),
  update: (provider: string, model: string, apiKey: string) => apiRequest<ModelProvider[]>('/api/model-connections', {
    method: 'PUT', body: JSON.stringify({ provider, model, apiKey }),
  }),
  test: () => apiRequest<ModelConnectionTest>('/api/model-connections/test', { method: 'POST' }),
}
