ALTER TABLE ai_invocation_log ADD COLUMN prompt_version VARCHAR(64);
ALTER TABLE ai_invocation_log ADD COLUMN request_hash VARCHAR(64);

CREATE TABLE ai_review_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    static_analysis_task_id BIGINT NOT NULL,
    invocation_id BIGINT NOT NULL,
    revision VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    retrieval_config_version VARCHAR(64),
    retrieval_mode VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    context_chunks INT NOT NULL DEFAULT 0,
    finding_count INT NOT NULL DEFAULT 0,
    rejected_findings INT NOT NULL DEFAULT 0,
    running_key VARCHAR(128),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_review_running UNIQUE (running_key),
    CONSTRAINT fk_ai_review_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_review_review
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_review_static_analysis
        FOREIGN KEY (static_analysis_task_id) REFERENCES static_analysis_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_review_invocation
        FOREIGN KEY (invocation_id) REFERENCES ai_invocation_log (id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_review_project_created
    ON ai_review_task (project_id, created_at);
CREATE INDEX idx_ai_review_review_task
    ON ai_review_task (review_task_id);

ALTER TABLE review_finding ADD COLUMN ai_review_task_id BIGINT;
ALTER TABLE review_finding ADD COLUMN chunk_id BIGINT;
ALTER TABLE review_finding ADD COLUMN conclusion_type VARCHAR(32);
ALTER TABLE review_finding ADD COLUMN confidence DECIMAL(5, 4);
ALTER TABLE review_finding ADD COLUMN risk_scenario TEXT;
ALTER TABLE review_finding ADD COLUMN suggestion TEXT;
ALTER TABLE review_finding ADD COLUMN verification TEXT;

ALTER TABLE review_finding
    ADD CONSTRAINT fk_review_finding_ai_review
    FOREIGN KEY (ai_review_task_id) REFERENCES ai_review_task (id) ON DELETE CASCADE;
ALTER TABLE review_finding
    ADD CONSTRAINT fk_review_finding_chunk
    FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_review_finding_ai_fingerprint
    ON review_finding (ai_review_task_id, fingerprint);
CREATE INDEX idx_review_finding_ai_severity
    ON review_finding (ai_review_task_id, severity);
