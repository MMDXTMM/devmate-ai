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
            "index_task",
            "conversation",
            "conversation_message",
            "bug_analysis",
            "ai_invocation_log",
            "tool_call_log"
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
    }
}
