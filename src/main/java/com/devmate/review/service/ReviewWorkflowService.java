package com.devmate.review.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.service.EmbeddingIndexService;
import com.devmate.knowledge.service.SourceImportService;
import com.devmate.project.service.ProjectService;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.dto.CreateAiReviewRequest;
import com.devmate.review.dto.CreateReviewDiffRequest;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.dto.ReviewWorkflowResponse;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.entity.ReviewWorkflowRun;
import com.devmate.review.model.ReviewWorkflowStage;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ReviewWorkflowService {

    private final ProjectService projectService;
    private final SourceImportService sourceImportService;
    private final ReviewDiffService reviewDiffService;
    private final StaticAnalysisService staticAnalysisService;
    private final EmbeddingIndexService embeddingIndexService;
    private final AgentAiReviewService agentAiReviewService;
    private final ReviewWorkflowStateService stateService;

    public ReviewWorkflowService(
            ProjectService projectService,
            SourceImportService sourceImportService,
            ReviewDiffService reviewDiffService,
            StaticAnalysisService staticAnalysisService,
            EmbeddingIndexService embeddingIndexService,
            AgentAiReviewService agentAiReviewService,
            ReviewWorkflowStateService stateService
    ) {
        this.projectService = projectService;
        this.sourceImportService = sourceImportService;
        this.reviewDiffService = reviewDiffService;
        this.staticAnalysisService = staticAnalysisService;
        this.embeddingIndexService = embeddingIndexService;
        this.agentAiReviewService = agentAiReviewService;
        this.stateService = stateService;
    }

    public ReviewWorkflowResponse create(Long projectId, String attemptKey) {
        projectService.getProject(projectId);
        ReviewWorkflowStart start = stateService.prepare(projectId, attemptKey);
        if (!start.created()) {
            return stateService.response(start.run());
        }

        ReviewWorkflowRun run = start.run();
        ReviewWorkflowStage stage = ReviewWorkflowStage.SOURCE_IMPORT;
        IndexTaskResponse sourceImport = null;
        ReviewDiffResponse reviewDiff = null;
        StaticAnalysisResponse staticAnalysis = null;
        EmbeddingIndexTaskResponse embeddingIndex = null;
        AiReviewResponse aiReview = null;
        try {
            sourceImport = sourceImportService.importSource(projectId);
            run = stateService.sourceImported(run.getId(), sourceImport.id());

            stage = ReviewWorkflowStage.DIFF;
            reviewDiff = reviewDiffService.create(projectId, new CreateReviewDiffRequest(null, null));
            run = stateService.diffCompleted(run.getId(), reviewDiff.id());

            stage = ReviewWorkflowStage.STATIC_ANALYSIS;
            staticAnalysis = staticAnalysisService.create(projectId);
            run = stateService.staticAnalysisCompleted(run.getId(), staticAnalysis.id());

            stage = ReviewWorkflowStage.EMBEDDING;
            embeddingIndex = embeddingIndexService.index(projectId);
            run = stateService.embeddingCompleted(run.getId(), embeddingIndex.id());

            stage = ReviewWorkflowStage.AGENT_REVIEW;
            aiReview = agentAiReviewService.create(projectId, new CreateAiReviewRequest(
                    reviewDiff.id(), reviewDiff.targetRevision(), UUID.randomUUID().toString()
            ));
            run = stateService.complete(run.getId(), aiReview.id());
            return stateService.response(
                    run, sourceImport, reviewDiff, staticAnalysis, embeddingIndex, aiReview
            );
        } catch (RuntimeException exception) {
            String message = failureMessage(stage, exception);
            run = stateService.fail(run.getId(), stage, message, recoveryAction(stage));
            return stateService.response(
                    run, sourceImport, reviewDiff, staticAnalysis, embeddingIndex, aiReview
            );
        }
    }

    public ReviewWorkflowResponse latest(Long projectId) {
        projectService.getProject(projectId);
        return stateService.response(stateService.latest(projectId));
    }

    private String failureMessage(ReviewWorkflowStage stage, RuntimeException exception) {
        if (exception instanceof BusinessException businessException
                && businessException.getErrorCode() != ErrorCode.INTERNAL_ERROR) {
            return businessException.getMessage();
        }
        return switch (stage) {
            case SOURCE_IMPORT -> "源码解析失败，请检查仓库和数据库迁移状态";
            case DIFF -> "Git变更范围生成失败";
            case STATIC_ANALYSIS -> "静态分析执行失败";
            case EMBEDDING -> "RAG索引构建失败";
            case AGENT_REVIEW -> "Agent代码审查失败，请检查模型配置或额度";
            case COMPLETED -> "代码审查运行失败";
        };
    }

    private String recoveryAction(ReviewWorkflowStage stage) {
        return switch (stage) {
            case SOURCE_IMPORT -> "确认仓库地址、分支、访问权限和V20迁移后重新执行";
            case DIFF -> "确认仓库至少包含两次提交后重新执行";
            case STATIC_ANALYSIS -> "查看静态分析子任务错误并修复源码范围问题后重试";
            case EMBEDDING -> "检查Embedding配置，或使用本地Embedding Provider后重试";
            case AGENT_REVIEW -> "配置模型密钥并检查额度；遇到429时不要立即重复请求";
            case COMPLETED -> "刷新页面后重新执行";
        };
    }
}
