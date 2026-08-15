import { projectApi } from './projectApi'

type ProjectUnderstandingApi = Pick<typeof projectApi, 'importSource' | 'indexEmbeddings'>

export async function runProjectUnderstanding(
  projectId: string,
  api: ProjectUnderstandingApi = projectApi,
) {
  const sourceImport = await api.importSource(projectId)
  const embeddingIndex = await api.indexEmbeddings(projectId)

  return { sourceImport, embeddingIndex }
}
