import type {
  EmbeddingIndexTask,
  IndexTask,
  PageData,
  Project,
  ProjectForm,
  ProjectQuery,
  SourceDocument,
  SourceSymbol,
  SourceSymbolDetail,
  SourceReference,
  ProjectBusinessMap,
  BusinessFeatureDetail,
  ReviewDiff,
  CreateAiReviewRequest,
  StaticAnalysis,
  RetrievalSearch,
  RetrievalSearchForm,
  AiReview,
  ReviewFeedback,
  ReviewFeedbackForm,
  ReviewWorkflow,
  ReviewEvaluationCase,
  ReviewEvaluationCaseForm,
  ReviewEvaluationRun,
} from '../types/project'
import { ApiError, apiRequest as request } from './apiClient'

export { ApiError }

export const projectApi = {
  list(query: ProjectQuery): Promise<PageData<Project>> {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    })
    if (query.name?.trim()) params.set('name', query.name.trim())
    if (query.status) params.set('status', query.status)
    return request(`/api/projects?${params.toString()}`)
  },

  create(form: ProjectForm): Promise<Project> {
    return request('/api/projects', {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  update(id: string, form: ProjectForm): Promise<Project> {
    return request(`/api/projects/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(form),
    })
  },

  delete(id: string): Promise<void> {
    return request(`/api/projects/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
  },

  importSource(id: string): Promise<IndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/imports`, {
      method: 'POST',
    })
  },

  rebuildSource(id: string): Promise<IndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/imports/rebuild`, {
      method: 'POST',
    })
  },

  latestImport(id: string): Promise<IndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/imports/latest`)
  },

  indexEmbeddings(id: string): Promise<EmbeddingIndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/embeddings/index`, {
      method: 'POST',
    })
  },

  latestEmbeddingIndex(id: string): Promise<EmbeddingIndexTask> {
    return request(`/api/projects/${encodeURIComponent(id)}/embeddings/tasks/latest`)
  },

  listSourceDocuments(id: string): Promise<SourceDocument[]> {
    return request(`/api/projects/${encodeURIComponent(id)}/sources`)
  },

  listSourceSymbols(projectId: string, documentId: string): Promise<SourceSymbol[]> {
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/sources/${encodeURIComponent(documentId)}/symbols`,
    )
  },

  getSourceSymbolDetail(
    projectId: string,
    documentId: string,
    symbolId: string,
  ): Promise<SourceSymbolDetail> {
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/sources/${encodeURIComponent(documentId)}/symbols/${encodeURIComponent(symbolId)}`,
    )
  },

  listSourceReferences(id: string): Promise<SourceReference[]> {
    return request(`/api/projects/${encodeURIComponent(id)}/sources/references`)
  },

  getBusinessMap(id: string): Promise<ProjectBusinessMap> {
    return request(`/api/projects/${encodeURIComponent(id)}/business-map`)
  },

  getBusinessFeatureDetail(id: string, featureId: string): Promise<BusinessFeatureDetail> {
    return request(
      `/api/projects/${encodeURIComponent(id)}/business-map/features/${encodeURIComponent(featureId)}`,
    )
  },

  createReviewDiff(id: string): Promise<ReviewDiff> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-diffs`, {
      method: 'POST',
      body: '{}',
    })
  },

  latestReviewDiff(id: string): Promise<ReviewDiff> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-diffs/latest`)
  },

  createStaticAnalysis(id: string): Promise<StaticAnalysis> {
    return request(`/api/projects/${encodeURIComponent(id)}/static-analyses`, {
      method: 'POST',
    })
  },

  latestStaticAnalysis(id: string): Promise<StaticAnalysis> {
    return request(`/api/projects/${encodeURIComponent(id)}/static-analyses/latest`)
  },

  createAiReview(id: string, form: CreateAiReviewRequest): Promise<AiReview> {
    return request(`/api/projects/${encodeURIComponent(id)}/ai-reviews`, {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  createAgentAiReview(id: string, form: CreateAiReviewRequest): Promise<AiReview> {
    return request(`/api/projects/${encodeURIComponent(id)}/ai-reviews/agent`, {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  latestAiReview(id: string): Promise<AiReview> {
    return request(`/api/projects/${encodeURIComponent(id)}/ai-reviews/latest`)
  },

  createReviewWorkflow(
    id: string,
    attemptKey = crypto.randomUUID(),
  ): Promise<ReviewWorkflow> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-workflows`, {
      method: 'POST',
      body: JSON.stringify({ attemptKey }),
    })
  },

  latestReviewWorkflow(id: string): Promise<ReviewWorkflow> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-workflows/latest`)
  },

  createReviewEvaluationCase(
    projectId: string,
    form: ReviewEvaluationCaseForm,
  ): Promise<ReviewEvaluationCase> {
    return request(`/api/projects/${encodeURIComponent(projectId)}/review-evaluation-cases`, {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  listReviewEvaluationCases(
    projectId: string,
    datasetVersion: string,
    reviewTaskId: string,
  ): Promise<ReviewEvaluationCase[]> {
    const params = new URLSearchParams({ datasetVersion, reviewTaskId })
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/review-evaluation-cases?${params.toString()}`,
    )
  },

  runReviewEvaluation(
    projectId: string,
    datasetVersion: string,
    aiReviewTaskId: string,
  ): Promise<ReviewEvaluationRun> {
    return request(`/api/projects/${encodeURIComponent(projectId)}/review-evaluation-runs`, {
      method: 'POST',
      body: JSON.stringify({ datasetVersion, aiReviewTaskId }),
    })
  },

  listReviewEvaluationRuns(
    projectId: string,
    datasetVersion: string,
    reviewTaskId: string,
  ): Promise<ReviewEvaluationRun[]> {
    const params = new URLSearchParams({ datasetVersion, reviewTaskId })
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/review-evaluation-runs?${params.toString()}`,
    )
  },

  upsertReviewFeedback(
    projectId: string,
    findingId: string,
    form: ReviewFeedbackForm,
  ): Promise<ReviewFeedback> {
    return request(
      `/api/projects/${encodeURIComponent(projectId)}/review-findings/${encodeURIComponent(findingId)}/feedback`,
      {
        method: 'PUT',
        body: JSON.stringify(form),
      },
    )
  },

  searchRetrieval(id: string, form: RetrievalSearchForm): Promise<RetrievalSearch> {
    return request(`/api/projects/${encodeURIComponent(id)}/retrieval/search`, {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },

  retrieveLatestDiffContext(id: string, form: RetrievalSearchForm): Promise<RetrievalSearch> {
    return request(`/api/projects/${encodeURIComponent(id)}/review-diffs/latest/context`, {
      method: 'POST',
      body: JSON.stringify(form),
    })
  },
}
