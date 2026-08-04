CREATE TABLE static_analysis_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    tool_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    analyzed_files INT NOT NULL DEFAULT 0,
    finding_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_static_analysis_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_static_analysis_review
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE
);

CREATE INDEX idx_static_analysis_project_created
    ON static_analysis_task (project_id, created_at);
CREATE INDEX idx_static_analysis_review
    ON static_analysis_task (review_task_id);

CREATE TABLE review_finding (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    review_task_id BIGINT NOT NULL,
    analysis_task_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    rule_id VARCHAR(255) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    path_hash VARCHAR(64) NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    message VARCHAR(1000) NOT NULL,
    evidence TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_finding_task_fingerprint
        UNIQUE (analysis_task_id, fingerprint),
    CONSTRAINT fk_review_finding_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_finding_review
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_finding_analysis
        FOREIGN KEY (analysis_task_id) REFERENCES static_analysis_task (id) ON DELETE CASCADE
);

CREATE INDEX idx_review_finding_task_severity
    ON review_finding (analysis_task_id, severity);
CREATE INDEX idx_review_finding_file_line
    ON review_finding (review_task_id, path_hash, start_line);
