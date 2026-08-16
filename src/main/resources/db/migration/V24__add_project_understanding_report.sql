CREATE TABLE project_understanding_report (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    revision VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    report_json MEDIUMTEXT,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    attempt_key CHAR(36) NOT NULL,
    running_key VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_understanding_user_attempt UNIQUE (user_id, attempt_key),
    CONSTRAINT uk_understanding_running UNIQUE (running_key),
    CONSTRAINT fk_understanding_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_understanding_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_understanding_project_latest
    ON project_understanding_report (project_id, created_at, id);
