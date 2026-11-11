package com.devmate.agent.service;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/** Adapts the terminal message-only chunk emitted by the configured compatible gateway. */
final class CompatibleStreamingOpenAiApi extends OpenAiApi {

    CompatibleStreamingOpenAiApi(String baseUrl, String apiKey,
                                RestClient.Builder restClient, WebClient.Builder webClient) {
        super(baseUrl, new SimpleApiKey(apiKey), new LinkedMultiValueMap<>(),
                "/chat/completions", "/embeddings", restClient, webClient,
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request,
                                                          MultiValueMap<String, String> headers) {
        return super.chatCompletionStream(request, headers).map(chunk -> {
            if (chunk.choices() == null) {
                throw new IllegalStateException("Invalid compatible stream choices");
            }
            var choices = chunk.choices().stream().map(choice -> {
                if (choice.delta() != null) return choice;
                if (choice.finishReason() == null) {
                    throw new IllegalStateException("Invalid compatible stream delta");
                }
                // Text has already arrived as deltas. Keep finish/usage metadata and do not
                // append the gateway's terminal full message a second time.
                return new ChatCompletionChunk.ChunkChoice(choice.finishReason(), choice.index(),
                        new ChatCompletionMessage("", ChatCompletionMessage.Role.ASSISTANT), choice.logprobs());
            }).toList();
            return new ChatCompletionChunk(chunk.id(), choices, chunk.created(), chunk.model(),
                    chunk.serviceTier(), chunk.systemFingerprint(), chunk.object(), chunk.usage());
        });
    }
}
