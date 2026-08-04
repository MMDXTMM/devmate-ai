package com.devmate.knowledge.source;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseSchemaParserTest {

    private final DatabaseSchemaParser parser = new DatabaseSchemaParser();

    @Test
    void extractsTablesColumnsIndexesAndAlterationsWithoutPersistingDefaultsOrData() {
        String sql = """
                CREATE TABLE app_user (
                    id BIGINT NOT NULL,
                    username VARCHAR(64) NOT NULL,
                    password_hash VARCHAR(255) DEFAULT 'do-not-store',
                    PRIMARY KEY (id),
                    CONSTRAINT uk_app_user_username UNIQUE (username)
                );
                CREATE INDEX idx_app_user_username ON app_user (username);
                ALTER TABLE app_user ADD COLUMN email VARCHAR(255);
                INSERT INTO app_user VALUES (1, 'admin', 'secret', 'admin@example.com');
                """;

        var chunks = parser.parseContent("db/migration/V1__init.sql", sql);

        assertThat(chunks).extracting(ParsedSourceChunk::symbolName).contains(
                "app_user", "app_user.id", "app_user.username", "app_user.password_hash",
                "app_user.email", "app_user#idx_app_user_username"
        );
        assertThat(chunks).extracting(ParsedSourceChunk::chunkType).contains(
                "DATABASE_TABLE", "DATABASE_COLUMN", "DATABASE_INDEX"
        );
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain("do-not-store", "admin@example.com", "secret"));
        assertThat(chunks).filteredOn(chunk -> chunk.symbolName().equals("app_user.id"))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.metadata()).containsEntry("nullable", false));
    }

    @Test
    void keepsStatementLinesAndHandlesSemicolonInsideStringLiteral() {
        var chunks = parser.parseContent("db/migration/V2__demo.sql", """
                -- first migration
                CREATE TABLE demo (
                    id BIGINT,
                    note VARCHAR(100) DEFAULT 'a;b'
                );
                """);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.startLine()).isEqualTo(2);
            assertThat(chunk.endLine()).isEqualTo(5);
        });
    }

    @Test
    void rejectsMalformedRecognizedDdl() {
        assertThatThrownBy(() -> parser.parseContent(
                "db/migration/V3__broken.sql", "CREATE TABLE broken (id BIGINT;"
        ))
                .isInstanceOf(SourceImportException.class)
                .hasMessageContaining("数据库迁移解析失败");
    }

    @Test
    void parsesEveryCurrentFlywayMigration() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".sql")).toList())
                    .isNotEmpty()
                    .allSatisfy(path -> assertThat(parser.parseContent(
                            path.toString(),
                            Files.readString(path)
                    )).isNotNull());
        }
    }
}
