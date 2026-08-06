ALTER TABLE embedding_vector
    ADD COLUMN input_hash VARCHAR(64);

CREATE INDEX idx_embedding_vector_reuse
    ON embedding_vector (project_id, revision, provider, model_name, dimensions, input_hash);

ALTER TABLE embedding_index_task
    ADD COLUMN reused_chunks INT NOT NULL DEFAULT 0;
