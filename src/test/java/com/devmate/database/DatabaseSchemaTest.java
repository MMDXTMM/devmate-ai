package com.devmate.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseSchemaTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "app_user",
            "project",
            "project_member",
            "knowledge_document",
            "knowledge_chunk",
            "code_reference",
            "index_task",
            "conversation",
            "conversation_message",
            "bug_analysis",
            "ai_invocation_log",
            "tool_call_log",
            "code_review_task",
            "code_review_file",
            "static_analysis_task",
            "review_finding",
            "code_review_feedback",
            "review_evaluation_case",
            "review_evaluation_run"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesCompleteInitialSchema() {
        for (String table : EXPECTED_TABLES) {
            Integer rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table,
                    Integer.class
            );
            assertThat(rowCount)
                    .as("table %s should exist and be queryable", table)
                    .isZero();
        }

        Integer pathHashColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(new_path_hash) FROM code_review_file",
                Integer.class
        );
        assertThat(pathHashColumn).isZero();
        Integer pathHashIndexColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.index_columns
                WHERE table_name = 'code_review_file'
                  AND index_name = 'idx_code_review_file_task_path_hash'
                  AND column_name IN ('review_task_id', 'new_path_hash')
                """, Integer.class);
        assertThat(pathHashIndexColumns).isEqualTo(2);

        Integer embeddingInputHashColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(input_hash) FROM embedding_vector",
                Integer.class
        );
        assertThat(embeddingInputHashColumn).isZero();
        Integer embeddingReusedChunksColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(reused_chunks) FROM embedding_index_task",
                Integer.class
        );
        assertThat(embeddingReusedChunksColumn).isZero();
        Integer embeddingReuseIndexColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.index_columns
                WHERE table_name = 'embedding_vector'
                  AND index_name = 'idx_embedding_vector_reuse'
                  AND column_name IN (
                    'project_id', 'revision', 'provider', 'model_name', 'dimensions', 'input_hash'
                  )
                """, Integer.class);
        assertThat(embeddingReuseIndexColumns).isEqualTo(6);
    }
}
