import { describe, expect, it, vi } from 'vitest'
import { ApiError, projectApi } from './projectApi'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('projectApi', () => {
  it('builds pagination and filter query parameters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: { page: 2, size: 10, total: 0, pages: 0, items: [] },
      timestamp: '2026-08-03T00:00:00Z',
    }))

    await projectApi.list({ page: 2, size: 10, name: ' devmate ', status: 'READY' })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects?page=2&size=10&name=devmate&status=READY',
      expect.any(Object),
    )
  })

  it('keeps project ids as strings when creating a project', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: { id: '2084116785588305922', name: 'devmate-ai' },
      timestamp: '2026-08-03T00:00:00Z',
    }, 201))

    const result = await projectApi.create({
      name: 'devmate-ai',
      description: '',
      sourceType: 'GIT',
      sourceLocation: 'https://github.com/MMDXTMM/devmate-ai.git',
      defaultBranch: 'main',
    })

    expect(result.id).toBe('2084116785588305922')
  })

  it('surfaces backend business errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 40000,
      message: 'Git项目必须填写仓库地址',
      timestamp: '2026-08-03T00:00:00Z',
    }, 400))

    await expect(projectApi.create({
      name: 'demo',
      description: '',
      sourceType: 'GIT',
      sourceLocation: '',
      defaultBranch: 'main',
    })).rejects.toEqual(expect.objectContaining<ApiError>({
      message: 'Git项目必须填写仓库地址',
      status: 400,
    }))
  })

  it('explains when the backend cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(projectApi.list({ page: 1, size: 10 })).rejects.toThrow(
      '无法连接后端服务，请确认 Spring Boot 已启动',
    )
  })

  it('starts a source import for the selected project', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588306000',
        projectId: '2084116785588305922',
        taskType: 'FULL',
        status: 'SUCCEEDED',
        totalFiles: 12,
        processedFiles: 12,
        failedFiles: 0,
      },
      timestamp: '2026-08-03T00:00:00Z',
    }))

    const task = await projectApi.importSource('2084116785588305922')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/imports',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(task.totalFiles).toBe(12)
  })

  it('loads source documents and symbols with string ids', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: [{
          id: '2084116785588307000',
          fileName: 'ReviewService.java',
          filePath: 'src/main/java/ReviewService.java',
          sourceKind: 'SOURCE_CODE',
          fileType: 'JAVA',
          status: 'PARSED',
          chunkCount: 2,
        }],
        timestamp: '2026-08-04T00:00:00Z',
      }))
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: [{
          id: '2084116785588307001',
          documentId: '2084116785588307000',
          chunkType: 'CLASS',
          symbolName: 'com.example.ReviewService',
          annotations: ['Service'],
          startLine: 3,
          endLine: 20,
        }],
        timestamp: '2026-08-04T00:00:00Z',
      }))

    const documents = await projectApi.listSourceDocuments('2084116785588305922')
    const symbols = await projectApi.listSourceSymbols(
      '2084116785588305922',
      documents[0].id,
    )

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/projects/2084116785588305922/sources',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/projects/2084116785588305922/sources/2084116785588307000/symbols',
      expect.any(Object),
    )
    expect(symbols[0].id).toBe('2084116785588307001')
  })

  it('creates a review diff coverage report', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588308000',
        projectId: '2084116785588305922',
        status: 'SUCCEEDED',
        changedFiles: 3,
        fullyMappedFiles: 1,
        partiallyMappedFiles: 1,
        skippedFiles: 1,
        files: [],
      },
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const result = await projectApi.createReviewDiff('2084116785588305922')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/review-diffs',
      expect.objectContaining({ method: 'POST', body: '{}' }),
    )
    expect(result.changedFiles).toBe(3)
  })

  it('loads persisted source references', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: [{
        id: '2084116785588307100',
        referenceKind: 'METHOD_CALL',
        referenceName: 'validate',
        sourceChunkId: '2084116785588307001',
        sourceSymbolName: 'com.example.ReviewService#review()',
        targetChunkId: '2084116785588307002',
        targetSymbolName: 'com.example.ReviewService#validate()',
        targetFilePath: 'src/main/java/ReviewService.java',
        startLine: 12,
        endLine: 12,
        resolved: true,
      }],
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const references = await projectApi.listSourceReferences('2084116785588305922')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/sources/references',
      expect.any(Object),
    )
    expect(references[0].targetSymbolName).toBe('com.example.ReviewService#validate()')
    expect(references[0].targetFilePath).toBe('src/main/java/ReviewService.java')
  })

  it('starts deterministic static analysis for the latest diff', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588309000',
        projectId: '2084116785588305922',
        reviewTaskId: '2084116785588308000',
        toolName: 'PMD',
        toolVersion: '7.26.0',
        status: 'SUCCEEDED',
        analyzedFiles: 1,
        findingCount: 1,
        findings: [],
      },
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const result = await projectApi.createStaticAnalysis('2084116785588305922')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/static-analyses',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(result.toolName).toBe('PMD')
  })

  it('starts an evidence-grounded AI review without sending credentials', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588311000',
        projectId: '2084116785588305922',
        reviewTaskId: '2084116785588308000',
        staticAnalysisTaskId: '2084116785588309000',
        invocationId: '2084116785588311001',
        provider: 'DASHSCOPE',
        modelName: 'qwen-plus',
        status: 'SUCCEEDED',
        contextChunks: 8,
        findingCount: 1,
        rejectedFindings: 0,
        totalTokens: 1200,
        latencyMs: 800,
        findings: [],
      },
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const result = await projectApi.createAiReview('2084116785588305922', {
      reviewTaskId: '2084116785588308000',
      revision: 'a'.repeat(40),
      attemptKey: '123e4567-e89b-42d3-a456-426614174000',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/ai-reviews',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          reviewTaskId: '2084116785588308000',
          revision: 'a'.repeat(40),
          attemptKey: '123e4567-e89b-42d3-a456-426614174000',
        }),
      }),
    )
    expect(result.modelName).toBe('qwen-plus')
  })

  it('starts the controlled agent review through its explicit endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588311000',
        projectId: '2084116785588305922',
        promptVersion: 'review-agent-v1',
        status: 'SUCCEEDED',
        findings: [],
        toolCalls: [],
      },
      timestamp: '2026-08-05T00:00:00Z',
    }))

    const result = await projectApi.createAgentAiReview('2084116785588305922', {
      reviewTaskId: '2084116785588308000',
      revision: 'b'.repeat(40),
      attemptKey: '123e4567-e89b-42d3-a456-426614174001',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/ai-reviews/agent',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          reviewTaskId: '2084116785588308000',
          revision: 'b'.repeat(40),
          attemptKey: '123e4567-e89b-42d3-a456-426614174001',
        }),
      }),
    )
    expect(result.promptVersion).toBe('review-agent-v1')
  })

  it('upserts developer feedback for a project-scoped finding', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588312000',
        projectId: '2084116785588305922',
        findingId: '2084116785588311002',
        feedbackType: 'FALSE_POSITIVE',
        comment: '调用方已经持有互斥锁',
        createdAt: '2026-08-05T00:00:00Z',
        updatedAt: '2026-08-05T00:00:00Z',
      },
      timestamp: '2026-08-05T00:00:00Z',
    }))

    const result = await projectApi.upsertReviewFeedback(
      '2084116785588305922',
      '2084116785588311002',
      { feedbackType: 'FALSE_POSITIVE', comment: '调用方已经持有互斥锁' },
    )

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/review-findings/2084116785588311002/feedback',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          feedbackType: 'FALSE_POSITIVE',
          comment: '调用方已经持有互斥锁',
        }),
      }),
    )
    expect(result.feedbackType).toBe('FALSE_POSITIVE')
  })

  it('creates and lists project-scoped review evaluation cases', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: { id: '21', projectId: '1', reviewTaskId: '2', datasetVersion: 'known-v1' },
        timestamp: '2026-08-05T00:00:00Z',
      }))
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: [],
        timestamp: '2026-08-05T00:00:00Z',
      }))

    const form = {
      reviewTaskId: '2',
      datasetVersion: 'known-v1',
      caseKey: 'lost-update',
      name: '库存丢失更新',
      expectationType: 'DEFECT' as const,
      category: 'CONCURRENCY' as const,
      filePath: 'src/OrderService.java',
      startLine: 20,
      endLine: 32,
      rationale: '并发请求可能同时通过库存检查',
    }

    await projectApi.createReviewEvaluationCase('1', form)
    await projectApi.listReviewEvaluationCases('1', 'known-v1', '2')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/projects/1/review-evaluation-cases',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(form) }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/projects/1/review-evaluation-cases?datasetVersion=known-v1&reviewTaskId=2',
      expect.any(Object),
    )
  })

  it('runs and lists evaluation snapshots without choosing an execution mode in the request', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: { id: '31', executionMode: 'AGENT', precision: 1, recall: 0.5, f1: 0.6667 },
        timestamp: '2026-08-05T00:00:00Z',
      }))
      .mockResolvedValueOnce(jsonResponse({
        code: 0,
        message: 'success',
        data: [],
        timestamp: '2026-08-05T00:00:00Z',
      }))

    await projectApi.runReviewEvaluation('1', 'known-v1', '9')
    await projectApi.listReviewEvaluationRuns('1', 'known-v1', '2')

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/projects/1/review-evaluation-runs',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ datasetVersion: 'known-v1', aiReviewTaskId: '9' }),
      }),
    )
    expect(fetchMock.mock.calls[0][1]?.body).not.toContain('executionMode')
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/projects/1/review-evaluation-runs?datasetVersion=known-v1&reviewTaskId=2',
      expect.any(Object),
    )
  })

  it('searches version-isolated context with an explicit token budget', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        projectId: '2084116785588305922',
        revision: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        query: 'transaction boundary',
        configVersion: 'lexical-graph-v1',
        requestedMode: 'HYBRID',
        executedMode: 'HYBRID',
        embeddingProvider: 'LOCAL',
        embeddingModel: 'code-hash-v1',
        vectorIndexAvailable: true,
        vectorCandidateCount: 3,
        vectorLimitReached: false,
        candidateCount: 8,
        candidateLimitReached: false,
        referenceLimitReached: false,
        topK: 5,
        tokenBudget: 2000,
        usedTokens: 420,
        selectedCount: 2,
        trimmedCount: 1,
        omittedTrimmedDetails: 0,
        hits: [],
        trimmed: [],
      },
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const result = await projectApi.searchRetrieval('2084116785588305922', {
      query: 'transaction boundary',
      topK: 5,
      tokenBudget: 2000,
      retrievalMode: 'HYBRID',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/retrieval/search',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          query: 'transaction boundary',
          topK: 5,
          tokenBudget: 2000,
          retrievalMode: 'HYBRID',
        }),
      }),
    )
    expect(result.configVersion).toBe('lexical-graph-v1')
  })

  it('starts an embedding index task without sending provider secrets', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      code: 0,
      message: 'success',
      data: {
        id: '2084116785588310000',
        projectId: '2084116785588305922',
        revision: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        provider: 'LOCAL',
        modelName: 'code-hash-v1',
        dimensions: 256,
        status: 'SUCCEEDED',
        totalChunks: 10,
        processedChunks: 10,
        skippedChunks: 0,
        failedChunks: 0,
        createdAt: '2026-08-04T00:00:00Z',
      },
      timestamp: '2026-08-04T00:00:00Z',
    }))

    const result = await projectApi.indexEmbeddings('2084116785588305922')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/2084116785588305922/embeddings/index',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(result.processedChunks).toBe(10)
  })
})
