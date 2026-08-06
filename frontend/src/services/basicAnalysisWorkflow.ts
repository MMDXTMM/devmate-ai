import { projectApi } from './projectApi'

type BasicAnalysisApi = Pick<
  typeof projectApi,
  'importSource' | 'createReviewDiff' | 'createStaticAnalysis' | 'indexEmbeddings'
>

export async function runBasicAnalysis(
  projectId: string,
  api: BasicAnalysisApi = projectApi,
) {
  const sourceImport = await api.importSource(projectId)
  const diff = await api.createReviewDiff(projectId)
  const staticAnalysis = await api.createStaticAnalysis(projectId)
  const embeddingIndex = await api.indexEmbeddings(projectId)

  return { sourceImport, diff, staticAnalysis, embeddingIndex }
}
