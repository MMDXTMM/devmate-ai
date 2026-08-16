package com.devmate.agent.service;

import com.devmate.agent.model.ModelConnectionSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Creates short-lived Spring AI clients for account-scoped, OpenAI-compatible model connections.
 * Provider URLs are validated by the caller; this class never accepts a URL directly from an API request.
 */
@Component
public class SpringAiChatClientFactory {

    private final RestClient.Builder restClientBuilder;
    private final boolean configureTimeouts;

    @Autowired
    public SpringAiChatClientFactory(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, true);
    }

    public SpringAiChatClientFactory(RestClient.Builder restClientBuilder, boolean configureTimeouts) {
        this.restClientBuilder = restClientBuilder;
        this.configureTimeouts = configureTimeouts;
    }

    public String chat(String baseUrl, String apiKey, String model,
                       String systemPrompt, String userPrompt, Integer maxTokens) {
        ModelConnectionSnapshot connection = new ModelConnectionSnapshot(
                "OPENAI_COMPATIBLE", model, baseUrl, apiKey
        );
        return chat(connection, systemPrompt, userPrompt, maxTokens,
                Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    public String chat(ModelConnectionSnapshot connection, String systemPrompt,
                       String userPrompt, Integer maxTokens,
                       Duration connectTimeout, Duration readTimeout) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
        String content = ChatClient.create(createChatModel(
                        connection, options.build(), connectTimeout, readTimeout
                ))
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new EmptyModelResponseException();
        }
        return content.trim();
    }

    public ChatClient createChatClient(ModelConnectionSnapshot connection,
                                       OpenAiChatOptions options,
                                       Duration connectTimeout,
                                       Duration readTimeout) {
        return ChatClient.create(createChatModel(connection, options, connectTimeout, readTimeout));
    }

    public OpenAiChatModel createChatModel(ModelConnectionSnapshot connection,
                                           OpenAiChatOptions options,
                                           Duration connectTimeout,
                                           Duration readTimeout) {
        OpenAiApi openAiApi = createApi(connection, connectTimeout, readTimeout);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build())
                .build();
    }

    public OpenAiEmbeddingModel createEmbeddingModel(ModelConnectionSnapshot connection,
                                                      int dimensions,
                                                      Duration connectTimeout,
                                                      Duration readTimeout) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(connection.model())
                .dimensions(dimensions)
                .encodingFormat("float")
                .build();
        return new OpenAiEmbeddingModel(
                createApi(connection, connectTimeout, readTimeout),
                MetadataMode.NONE,
                options,
                RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build()
        );
    }

    private OpenAiApi createApi(ModelConnectionSnapshot connection,
                                Duration connectTimeout,
                                Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        RestClient.Builder clientBuilder = restClientBuilder.clone();
        if (configureTimeouts) {
            clientBuilder.requestFactory(requestFactory);
        }
        return OpenAiApi.builder()
                .baseUrl(connection.baseUrl())
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .apiKey(connection.apiKey())
                .restClientBuilder(clientBuilder)
                .build();
    }

    public static final class EmptyModelResponseException extends RuntimeException {
        public EmptyModelResponseException() {
            super("Model returned an empty response");
        }
    }
}
