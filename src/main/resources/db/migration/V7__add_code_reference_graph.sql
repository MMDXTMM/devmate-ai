CREATE TABLE code_reference (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_chunk_id BIGINT NOT NULL,
    target_chunk_id BIGINT,
    revision VARCHAR(64) NOT NULL,
    reference_kind VARCHAR(32) NOT NULL,
    reference_name VARCHAR(500) NOT NULL,
    qualifier VARCHAR(500),
    argument_count INT,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_code_reference_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_reference_source_chunk
        FOREIGN KEY (source_chunk_id) REFERENCES knowledge_chunk (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_reference_target_chunk
        FOREIGN KEY (target_chunk_id) REFERENCES knowledge_chunk (id) ON DELETE SET NULL
);

CREATE INDEX idx_code_reference_project_revision
    ON code_reference (project_id, revision, reference_kind);
CREATE INDEX idx_code_reference_source
    ON code_reference (source_chunk_id, reference_kind);
CREATE INDEX idx_code_reference_target
    ON code_reference (target_chunk_id, reference_kind);
