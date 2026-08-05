package com.devmate.review.service;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.AiReviewException;
import com.devmate.agent.model.AiReviewModel;
import com.devmate.agent.model.AiReviewModelRegistry;
import com.devmate.agent.model.AiReviewModelResult;
import com.devmate.agent.model.AiReviewPrompt;
import com.devmate.agent.model.ReviewAgentResearchResult;
import com.devmate.agent.service.AiReviewPromptBuilder;
import com.devmate.agent.service.ReviewAgentOrchestrator;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.model.AiFindingValidationResult;
import com.devmate.review.model.ReviewExecutionMode;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AgentAiReviewService {

    private final AiReviewModelRegistry modelRegistry;
    private final ReviewAgentProperties properties;
    private final AiReviewStateService stateService;
    private final ReviewAgentOrchestrator orchestrator;
    private final AiReviewPromptBuilder promptBuilder;
    private final AiFindingValidator findingValidator;

    public AgentAiReviewService(
            AiReviewModelRegistry modelRegistry,
            ReviewAgentProperties properties,
            AiReviewStateService stateService,
            ReviewAgentOrchestrator orchestrator,
            AiReviewPromptBuilder promptBuilder,
            AiFindingValidator findingValidator
    ) {
        this.modelRegistry = modelRegistry;
        this.properties = properties;
        this.stateService = stateService;
        this.orchestrator = orchestrator;
        this.promptBuilder = promptBuilder;
        this.findingValidator = findingValidator;
    }

    public AiReviewResponse create(Long projectId) {
        AiReviewModel model = modelRegistry.current();
        AiReviewContext context = stateService.prepare(
                projectId, model.providerName(), model.modelName(), properties.getPromptVersion(),
                ReviewExecutionMode.AGENT
        );
        long startedAt = System.nanoTime();
        AiReviewPrompt prompt = null;
        try {
            ReviewAgentResearchResult research = orchestrator.research(context);
            prompt = promptBuilder.build(
                    context.reviewTask(), research.retrieval(), context.staticFindings(),
                    properties.getPromptVersion()
            );
            AiReviewModelResult finalResult = model.review(prompt);
            AiFindingValidationResult validation = findingValidator.validate(
                    finalResult.findings(), research.retrieval().hits()
            );
            AiReviewModelResult combined = combine(research, finalResult);
            return stateService.complete(
                    context, research.retrieval(), validation, combined,
                    elapsedMillis(startedAt), prompt.requestHash()
            );
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMillis(startedAt);
            String message = safeMessage(exception);
            stateService.fail(
                    context, exception.getClass().getSimpleName(), message, latencyMs,
                    prompt == null ? null : prompt.requestHash()
            );
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            if (exception instanceof AiReviewException) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Agent审查执行失败");
        }
    }

    private AiReviewModelResult combine(
            ReviewAgentResearchResult research,
            AiReviewModelResult finalResult
    ) {
        return new AiReviewModelResult(
                finalResult.findings(),
                research.promptTokens() + finalResult.promptTokens(),
                research.completionTokens() + finalResult.completionTokens(),
                research.totalTokens() + finalResult.totalTokens(),
                finalResult.finishReason()
        );
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof AiReviewException) {
            return exception.getMessage() == null ? "Agent审查执行失败" : exception.getMessage();
        }
        return "Agent审查执行失败";
    }
}
