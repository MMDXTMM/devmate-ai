CREATE TABLE retrieval_evaluation_case (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    query_text VARCHAR(500) NOT NULL,
    expected_file_path VARCHAR(1000) NOT NULL,
    expected_symbol_name VARCHAR(500),
    top_k INT NOT NULL DEFAULT 5,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_retrieval_case_version_name
        UNIQUE (project_id, dataset_version, name),
    CONSTRAINT fk_retrieval_case_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_case_project_version
    ON retrieval_evaluation_case (project_id, dataset_version, enabled);

CREATE TABLE retrieval_evaluation_run (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    revision VARCHAR(64) NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    retrieval_config_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_cases INT NOT NULL DEFAULT 0,
    resolved_cases INT NOT NULL DEFAULT 0,
    recall_at_k DECIMAL(8, 6),
    precision_at_k DECIMAL(8, 6),
    hit_rate_at_k DECIMAL(8, 6),
    mean_reciprocal_rank DECIMAL(8, 6),
    result_json TEXT,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_retrieval_run_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_run_project_created
    ON retrieval_evaluation_run (project_id, created_at);
CREATE INDEX idx_retrieval_run_dataset
    ON retrieval_evaluation_run (project_id, dataset_version, status);
