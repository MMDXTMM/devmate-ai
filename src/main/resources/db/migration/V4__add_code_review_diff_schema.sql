CREATE TABLE code_review_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    index_task_id BIGINT NOT NULL,
    base_revision VARCHAR(64),
    target_revision VARCHAR(64),
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    changed_files INT NOT NULL DEFAULT 0,
    fully_mapped_files INT NOT NULL DEFAULT 0,
    partially_mapped_files INT NOT NULL DEFAULT 0,
    skipped_files INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_code_review_task_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_review_task_index_task
        FOREIGN KEY (index_task_id) REFERENCES index_task (id) ON DELETE CASCADE
);

CREATE INDEX idx_code_review_task_project_created
    ON code_review_task (project_id, created_at);
CREATE INDEX idx_code_review_task_status
    ON code_review_task (status);

CREATE TABLE code_review_file (
    id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    old_path VARCHAR(1000),
    new_path VARCHAR(1000),
    change_type VARCHAR(32) NOT NULL,
    coverage_status VARCHAR(32) NOT NULL,
    additions INT NOT NULL DEFAULT 0,
    deletions INT NOT NULL DEFAULT 0,
    changed_lines_json TEXT,
    mapped_symbols_json TEXT,
    skip_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_code_review_file_task
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_review_file_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_code_review_file_task_coverage
    ON code_review_file (review_task_id, coverage_status);
