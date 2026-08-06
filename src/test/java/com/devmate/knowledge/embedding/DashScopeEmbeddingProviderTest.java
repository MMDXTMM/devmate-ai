package com.devmate.knowledge.embedding;

import com.devmate.knowledge.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DashScopeEmbeddingProviderTest {

    @Test
    void callsOpenAiCompatibleEmbeddingEndpointWithoutLeakingKeyIntoPayload() {
        EmbeddingProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://embedding.example/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model":"text-embedding-v4",
                          "input":["first","second"],
                          "dimensions":64,
                          "encoding_format":"float"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model":"text-embedding-v4",
                          "data":[
                            {"index":1,"embedding":[%s]},
                            {"index":0,"embedding":[%s]}
                          ]
                        }
                        """.formatted(vector(1), vector(0)), MediaType.APPLICATION_JSON));

        DashScopeEmbeddingProvider provider = new DashScopeEmbeddingProvider(properties, builder);
        EmbeddingBatch result = provider.embed(List.of("first", "second"));

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().getFirst()).hasSize(64);
        assertThat(result.vectors().getFirst()[0]).isEqualTo(1.0f);
        server.verify();
    }

    @Test
    void rejectsMissingCredentialsBeforeAnyNetworkCall() {
        EmbeddingProperties properties = properties();
        properties.setApiKey("");
        DashScopeEmbeddingProvider provider = new DashScopeEmbeddingProvider(
                properties,
                RestClient.builder()
        );

        assertThatThrownBy(() -> provider.embed(List.of("code")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessage("未配置远端Embedding API Key");
    }

    @Test
    void explainsRateLimitWithoutAutomaticallyRetryingTheBatch() {
        EmbeddingProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://embedding.example/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        DashScopeEmbeddingProvider provider = new DashScopeEmbeddingProvider(properties, builder);

        assertThatThrownBy(() -> provider.embed(List.of("code")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessage("远端Embedding请求过于频繁或额度不足，请稍后重试并检查额度");
        server.verify();
    }

    private EmbeddingProperties properties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl("https://embedding.example/v1");
        properties.setApiKey("test-key");
        properties.setModel("text-embedding-v4");
        properties.setDimensions(64);
        properties.setBatchSize(10);
        return properties;
    }

    private String vector(int oneAt) {
        return IntStream.range(0, 64)
                .mapToObj(index -> index == oneAt ? "1.0" : "0.0")
                .collect(Collectors.joining(","));
    }
}
