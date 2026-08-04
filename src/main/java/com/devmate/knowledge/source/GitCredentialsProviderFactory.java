package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class GitCredentialsProviderFactory {

    private static final String DEFAULT_USERNAME = "x-access-token";

    private final SourceImportProperties properties;

    public GitCredentialsProviderFactory(SourceImportProperties properties) {
        this.properties = properties;
    }

    public Optional<CredentialsProvider> create() {
        if (!StringUtils.hasText(properties.getGitToken())) {
            return Optional.empty();
        }
        String username = StringUtils.hasText(properties.getGitUsername())
                ? properties.getGitUsername().trim()
                : DEFAULT_USERNAME;
        return Optional.of(new UsernamePasswordCredentialsProvider(
                username,
                properties.getGitToken().trim()
        ));
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getGitToken());
    }
}
