import { projectApi } from './projectApi'

type ProjectUnderstandingApi = Pick<typeof projectApi, 'importSource' | 'indexEmbeddings'>

export async function runProjectUnderstanding(
  projectId: string,
  api: ProjectUnderstandingApi = projectApi,
  onStage?: (stage: 'SOURCE_IMPORT' | 'EMBEDDING' | 'COMPLETED') => void,
) {
  onStage?.('SOURCE_IMPORT')
  const sourceImport = await api.importSource(projectId)
  onStage?.('EMBEDDING')
  const embeddingIndex = await api.indexEmbeddings(projectId)
  onStage?.('COMPLETED')

  return { sourceImport, embeddingIndex }
}
