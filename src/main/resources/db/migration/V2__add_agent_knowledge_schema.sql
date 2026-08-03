CREATE TABLE app_user (
    id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

ALTER TABLE project ADD COLUMN owner_id BIGINT;
ALTER TABLE project ADD COLUMN current_revision VARCHAR(64);
ALTER TABLE project ADD COLUMN last_indexed_at TIMESTAMP;
ALTER TABLE project
    ADD CONSTRAINT fk_project_owner
    FOREIGN KEY (owner_id) REFERENCES app_user (id);

CREATE INDEX idx_project_owner ON project (owner_id);

CREATE TABLE project_member (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL DEFAULT 'DEVELOPER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_member_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_member_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_project_member_user ON project_member (user_id);

CREATE TABLE knowledge_document (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    path_hash VARCHAR(64) NOT NULL,
    file_type VARCHAR(32),
    content_hash VARCHAR(64) NOT NULL,
    revision VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_document_version
        UNIQUE (project_id, path_hash, revision),
    CONSTRAINT fk_knowledge_document_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_document_project_status
    ON knowledge_document (project_id, status);
CREATE INDEX idx_knowledge_document_hash
    ON knowledge_document (content_hash);

CREATE TABLE knowledge_chunk (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    symbol_name VARCHAR(500),
    language VARCHAR(32),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_id VARCHAR(128),
    token_count INT,
    start_line INT,
    end_line INT,
    revision VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_chunk_position
        UNIQUE (document_id, chunk_index),
    CONSTRAINT fk_knowledge_chunk_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunk_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_chunk_project ON knowledge_chunk (project_id);
CREATE INDEX idx_knowledge_chunk_vector ON knowledge_chunk (vector_id);
CREATE INDEX idx_knowledge_chunk_symbol ON knowledge_chunk (symbol_name);

CREATE TABLE index_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    revision VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_files INT NOT NULL DEFAULT 0,
    processed_files INT NOT NULL DEFAULT 0,
    failed_files INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_index_task_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_index_task_project_status
    ON index_task (project_id, status);
CREATE INDEX idx_index_task_created_at ON index_task (created_at);

CREATE TABLE conversation (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_conversation_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_conversation_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_conversation_user_project
    ON conversation (user_id, project_id);
CREATE INDEX idx_conversation_updated_at ON conversation (updated_at);

CREATE TABLE conversation_message (
    id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    message_role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    model_name VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversation_message_sequence
        UNIQUE (conversation_id, sequence_no),
    CONSTRAINT fk_conversation_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
);

CREATE INDEX idx_conversation_message_created_at
    ON conversation_message (conversation_id, created_at);

CREATE TABLE bug_analysis (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    conversation_id BIGINT,
    created_by BIGINT NOT NULL,
    title VARCHAR(255),
    error_log TEXT NOT NULL,
    analysis_result TEXT,
    severity VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bug_analysis_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_bug_analysis_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE SET NULL,
    CONSTRAINT fk_bug_analysis_creator
        FOREIGN KEY (created_by) REFERENCES app_user (id)
);

CREATE INDEX idx_bug_analysis_project_status
    ON bug_analysis (project_id, status);
CREATE INDEX idx_bug_analysis_creator ON bug_analysis (created_by);

CREATE TABLE ai_invocation_log (
    id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    project_id BIGINT,
    conversation_id BIGINT,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_invocation_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_invocation_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_invocation_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE SET NULL
);

CREATE INDEX idx_ai_invocation_trace ON ai_invocation_log (trace_id);
CREATE INDEX idx_ai_invocation_project_created
    ON ai_invocation_log (project_id, created_at);
CREATE INDEX idx_ai_invocation_status_created
    ON ai_invocation_log (status, created_at);

CREATE TABLE tool_call_log (
    id BIGINT NOT NULL,
    invocation_id BIGINT NOT NULL,
    project_id BIGINT,
    tool_name VARCHAR(100) NOT NULL,
    arguments_summary TEXT,
    result_summary TEXT,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tool_call_invocation
        FOREIGN KEY (invocation_id) REFERENCES ai_invocation_log (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_call_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE SET NULL
);

CREATE INDEX idx_tool_call_invocation ON tool_call_log (invocation_id);
CREATE INDEX idx_tool_call_name_created
    ON tool_call_log (tool_name, created_at);
