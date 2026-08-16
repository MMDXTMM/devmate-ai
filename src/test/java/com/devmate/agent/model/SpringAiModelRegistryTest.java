package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiModelRegistryTest {

    @Test
    void bindsBothReviewModesToTheActiveAccountModelSnapshot() {
        ModelConnectionService connections = mock(ModelConnectionService.class);
        SpringAiChatClientFactory clients = mock(SpringAiChatClientFactory.class);
        ModelConnectionSnapshot snapshot = new ModelConnectionSnapshot(
                "DEEPSEEK", "deepseek-v4-flash", "https://api.deepseek.com", "account-key"
        );
        when(connections.requireActiveConnection()).thenReturn(snapshot);
        AiReviewProperties properties = new AiReviewProperties();

        AiReviewModel fixed = new AiReviewModelRegistry(properties, connections, clients).current();
        ReviewAgentModel agent = new ReviewAgentModelRegistry(properties, connections, clients).current();

        assertThat(fixed.providerName()).isEqualTo("DEEPSEEK");
        assertThat(fixed.modelName()).isEqualTo("deepseek-v4-flash");
        assertThat(agent.providerName()).isEqualTo("DEEPSEEK");
        assertThat(agent.modelName()).isEqualTo("deepseek-v4-flash");
        assertThat(snapshot.toString()).contains("apiKey=REDACTED").doesNotContain("account-key");
    }

    @Test
    void rejectsAgentContinuationAfterTheAccountModelChanges() {
        ModelConnectionService connections = mock(ModelConnectionService.class);
        SpringAiChatClientFactory clients = mock(SpringAiChatClientFactory.class);
        when(connections.requireActiveConnection()).thenReturn(new ModelConnectionSnapshot(
                "DEEPSEEK", "deepseek-chat", "https://api.deepseek.com", "account-key"
        ));
        ReviewAgentModelRegistry registry = new ReviewAgentModelRegistry(
                new AiReviewProperties(), connections, clients
        );

        assertThatThrownBy(() -> registry.current("OPENAI", "gpt-5"))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("模型连接已切换，请重新发起审查");
    }
}
