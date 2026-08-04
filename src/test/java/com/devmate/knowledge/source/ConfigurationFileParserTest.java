package com.devmate.knowledge.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationFileParserTest {

    private final ConfigurationFileParser parser = new ConfigurationFileParser();

    @Test
    void flattensNestedYamlAndSequencesWithoutPersistingValues() {
        var chunks = parser.parseYaml("application.yml", """
                server:
                  port: 8080
                review:
                  enabled: true
                  rules:
                    - concurrency
                    - security
                datasource:
                  password: super-secret
                """);

        assertThat(chunks).extracting(ParsedSourceChunk::symbolName).containsExactly(
                "server.port",
                "review.enabled",
                "review.rules[0]",
                "review.rules[1]",
                "datasource.password"
        );
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain("8080", "true", "concurrency", "security", "super-secret"));
        assertThat(chunks.getLast().content()).isEqualTo("datasource.password = <redacted>");
        assertThat(chunks.getLast().metadata()).containsEntry("sensitive", true);
    }

    @Test
    void parsesPropertiesLogicalLinesAndRedactsSecrets() {
        var chunks = parser.parseProperties("application.properties", """
                review.limit=20
                review.description=long\\
                  text
                api-token=do-not-store
                """);

        assertThat(chunks).extracting(ParsedSourceChunk::symbolName).containsExactly(
                "review.limit", "review.description", "api-token"
        );
        assertThat(chunks.get(1).startLine()).isEqualTo(2);
        assertThat(chunks.get(1).endLine()).isEqualTo(3);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain("20", "longtext", "do-not-store"));
        assertThat(chunks.getLast().content()).isEqualTo("api-token = <redacted>");
    }

    @Test
    void rejectsDuplicateYamlKeys() {
        assertThatThrownBy(() -> parser.parseYaml("application.yml", "review:\n  limit: 10\n  limit: 20\n"))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("YAML配置解析失败");
    }

    @Test
    void rejectsDuplicatePropertiesKeys() {
        assertThatThrownBy(() -> parser.parseProperties(
                "application.properties", "review.limit=10\nreview.limit=20\n"
        ))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("Properties配置包含重复键");
    }
}
