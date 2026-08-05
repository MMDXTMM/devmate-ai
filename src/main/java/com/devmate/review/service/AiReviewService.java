package com.devmate.review.service;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.model.AiReviewException;
import com.devmate.agent.model.AiReviewModel;
import com.devmate.agent.model.AiReviewModelRegistry;
import com.devmate.agent.model.AiReviewModelResult;
import com.devmate.agent.model.AiReviewPrompt;
import com.devmate.agent.service.AiReviewPromptBuilder;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.retrieval.RetrievalMode;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.dto.ReviewContextRequest;
import com.devmate.review.model.AiFindingValidationResult;
import com.devmate.review.model.ReviewExecutionMode;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AiReviewService {

    private final AiReviewModelRegistry modelRegistry;
    private final AiReviewProperties properties;
    private final AiReviewStateService stateService;
    private final ReviewContextService reviewContextService;
    private final AiReviewPromptBuilder promptBuilder;
    private final AiFindingValidator findingValidator;

    public AiReviewService(
            AiReviewModelRegistry modelRegistry,
            AiReviewProperties properties,
            AiReviewStateService stateService,
            ReviewContextService reviewContextService,
            AiReviewPromptBuilder promptBuilder,
            AiFindingValidator findingValidator
    ) {
        this.modelRegistry = modelRegistry;
        this.properties = properties;
        this.stateService = stateService;
        this.reviewContextService = reviewContextService;
        this.promptBuilder = promptBuilder;
        this.findingValidator = findingValidator;
    }

    public AiReviewResponse create(Long projectId) {
        AiReviewModel model = modelRegistry.current();
        AiReviewContext context = stateService.prepare(
                projectId,
                model.providerName(),
                model.modelName(),
                properties.getPromptVersion(),
                ReviewExecutionMode.FIXED
        );
        long startedAt = System.nanoTime();
        AiReviewPrompt prompt = null;
        try {
            RetrievalSearchResponse retrieval = reviewContextService.retrieveForTask(
                    projectId,
                    context.reviewTask(),
                    new ReviewContextRequest(
                            null,
                            properties.getTopK(),
                            properties.getTokenBudget(),
                            RetrievalMode.HYBRID
                    )
            );
            prompt = promptBuilder.build(context.reviewTask(), retrieval, context.staticFindings());
            AiReviewModelResult modelResult = model.review(prompt);
            AiFindingValidationResult validation = findingValidator.validate(
                    modelResult.findings(),
                    retrieval.hits()
            );
            return stateService.complete(
                    context,
                    retrieval,
                    validation,
                    modelResult,
                    elapsedMillis(startedAt),
                    prompt.requestHash()
            );
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMillis(startedAt);
            String message = safeMessage(exception);
            stateService.fail(
                    context,
                    exception.getClass().getSimpleName(),
                    message,
                    latencyMs,
                    prompt == null ? null : prompt.requestHash()
            );
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            if (exception instanceof AiReviewException) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI审查执行失败");
        }
    }

    public AiReviewResponse getLatest(Long projectId) {
        return stateService.getLatest(projectId);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof AiReviewException) {
            return exception.getMessage() == null ? "AI审查执行失败" : exception.getMessage();
        }
        return "AI审查执行失败";
    }
}
