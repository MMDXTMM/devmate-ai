package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitCredentialsProviderFactoryTest {

    @Test
    void doesNotCreateCredentialsWithoutToken() {
        SourceImportProperties properties = new SourceImportProperties();

        GitCredentialsProviderFactory factory = new GitCredentialsProviderFactory(properties);

        assertThat(factory.create()).isEmpty();
        assertThat(factory.isConfigured()).isFalse();
    }

    @Test
    void createsCredentialsFromConfigurationWithoutLoggingOrPersistingThem() throws Exception {
        SourceImportProperties properties = new SourceImportProperties();
        properties.setGitUsername("MMDXTMM");
        properties.setGitToken("test-token-value");
        GitCredentialsProviderFactory factory = new GitCredentialsProviderFactory(properties);

        CredentialsProvider provider = factory.create().orElseThrow();
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();

        assertThat(provider.get(new URIish("https://github.com/MMDXTMM/devmate-ai.git"), username, password))
                .isTrue();
        assertThat(username.getValue()).isEqualTo("MMDXTMM");
        assertThat(password.getValue()).containsExactly("test-token-value".toCharArray());
        assertThat(factory.isConfigured()).isTrue();
    }
}
