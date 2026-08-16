package com.devmate.agent.model;

import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Map;

final class SpringAiProjectUnderstandingModel implements ProjectUnderstandingModel {
    private final ModelConnectionSnapshot connection;
    private final ProjectUnderstandingProperties properties;
    private final SpringAiChatClientFactory clientFactory;

    SpringAiProjectUnderstandingModel(
            ModelConnectionSnapshot connection,
            ProjectUnderstandingProperties properties,
            SpringAiChatClientFactory clientFactory
    ) {
        this.connection = connection;
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public String providerName() { return connection.provider(); }

    @Override
    public String modelName() { return connection.model(); }

    @Override
    public ProjectUnderstandingModelResult analyze(ProjectUnderstandingPrompt prompt) {
        try {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
            if ("DASHSCOPE".equals(connection.provider())) {
                options.extraBody(Map.of("enable_thinking", false));
            }
            ResponseEntity<ChatResponse, StructuredReport> response = clientFactory.createChatClient(
                            connection, options.build(),
                            properties.getConnectTimeout(), properties.getReadTimeout()
                    ).prompt()
                    .system(prompt.systemPrompt())
                    .user(prompt.userPrompt())
                    .call()
                    .responseEntity(StructuredReport.class);
            if (response.entity() == null) {
                throw new AiReviewException("项目理解模型未返回符合契约的结果");
            }
            Usage usage = response.response() == null ? null : response.response().getMetadata().getUsage();
            StructuredReport report = response.entity();
            return new ProjectUnderstandingModelResult(
                    report.executiveSummary(), report.architectureNarrative(), report.businessFlows(),
                    report.readingGuide(), report.risksAndUnknowns(),
                    token(usage == null ? null : usage.getPromptTokens()),
                    token(usage == null ? null : usage.getCompletionTokens()),
                    token(usage == null ? null : usage.getTotalTokens())
            );
        } catch (AiReviewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw SpringAiErrorTranslator.reviewFailure("项目理解模型调用", exception);
        }
    }

    private int token(Integer value) { return value == null ? 0 : value; }

    record StructuredReport(
            String executiveSummary,
            String architectureNarrative,
            List<ProjectUnderstandingModelResult.BusinessFlow> businessFlows,
            List<ProjectUnderstandingModelResult.ReadingGuide> readingGuide,
            List<String> risksAndUnknowns
    ) { }
}
