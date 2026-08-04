CREATE TABLE embedding_vector (
    vector_id VARCHAR(128) NOT NULL,
    project_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    revision VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    dimensions INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vector_id),
    CONSTRAINT uk_embedding_vector_version
        UNIQUE (chunk_id, provider, model_name, dimensions, content_hash),
    CONSTRAINT fk_embedding_vector_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_embedding_vector_chunk
        FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (id) ON DELETE CASCADE
);

CREATE INDEX idx_embedding_vector_scope
    ON embedding_vector (project_id, revision, provider, model_name, dimensions);

CREATE TABLE embedding_index_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    revision VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    dimensions INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_chunks INT NOT NULL DEFAULT 0,
    processed_chunks INT NOT NULL DEFAULT 0,
    skipped_chunks INT NOT NULL DEFAULT 0,
    failed_chunks INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_embedding_task_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_embedding_task_project_created
    ON embedding_index_task (project_id, created_at);

ALTER TABLE retrieval_evaluation_run
    ADD COLUMN retrieval_mode VARCHAR(32) NOT NULL DEFAULT 'LEXICAL';
