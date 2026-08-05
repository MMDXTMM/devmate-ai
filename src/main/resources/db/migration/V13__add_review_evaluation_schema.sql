ALTER TABLE ai_review_task
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'FIXED';

UPDATE ai_review_task
SET execution_mode = 'AGENT'
WHERE prompt_version LIKE 'review-agent-%';

CREATE TABLE review_evaluation_case (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    case_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    target_revision VARCHAR(64) NOT NULL,
    expectation_type VARCHAR(16) NOT NULL,
    category VARCHAR(64),
    file_path VARCHAR(1000),
    path_hash VARCHAR(64),
    start_line INT,
    end_line INT,
    rationale VARCHAR(1000) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_evaluation_case_key
        UNIQUE (project_id, dataset_version, case_key),
    CONSTRAINT fk_review_evaluation_case_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_evaluation_case_review_task
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE
);

CREATE INDEX idx_review_evaluation_case_dataset
    ON review_evaluation_case (project_id, dataset_version, review_task_id, enabled);
CREATE INDEX idx_review_evaluation_case_location
    ON review_evaluation_case (review_task_id, path_hash, start_line);

CREATE TABLE review_evaluation_run (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    ai_review_task_id BIGINT NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    dataset_hash VARCHAR(64) NOT NULL,
    execution_mode VARCHAR(16) NOT NULL,
    revision VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    retrieval_config_version VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    expected_defects INT NOT NULL DEFAULT 0,
    predicted_findings INT NOT NULL DEFAULT 0,
    true_positives INT NOT NULL DEFAULT 0,
    false_positives INT NOT NULL DEFAULT 0,
    false_negatives INT NOT NULL DEFAULT 0,
    manual_review_count INT NOT NULL DEFAULT 0,
    partial_metrics TINYINT NOT NULL DEFAULT 0,
    precision_score DECIMAL(8, 6) NOT NULL,
    recall_score DECIMAL(8, 6) NOT NULL,
    f1_score DECIMAL(8, 6) NOT NULL,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    tool_call_count INT NOT NULL DEFAULT 0,
    tool_success_count INT NOT NULL DEFAULT 0,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_evaluation_run_snapshot
        UNIQUE (ai_review_task_id, dataset_hash),
    CONSTRAINT fk_review_evaluation_run_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_evaluation_run_review_task
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_evaluation_run_ai_task
        FOREIGN KEY (ai_review_task_id) REFERENCES ai_review_task (id) ON DELETE CASCADE
);

CREATE INDEX idx_review_evaluation_run_dataset
    ON review_evaluation_run (project_id, dataset_version, review_task_id, created_at);
CREATE INDEX idx_review_evaluation_run_mode
    ON review_evaluation_run (review_task_id, execution_mode, created_at);
