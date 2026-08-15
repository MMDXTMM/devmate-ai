CREATE TABLE review_workflow_run (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    attempt_key VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(32) NOT NULL,
    index_task_id BIGINT,
    review_task_id BIGINT,
    static_analysis_task_id BIGINT,
    embedding_task_id BIGINT,
    ai_review_task_id BIGINT,
    running_key VARCHAR(64),
    error_message VARCHAR(1000),
    recovery_action VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_workflow_attempt UNIQUE (attempt_key),
    CONSTRAINT uk_review_workflow_running UNIQUE (running_key),
    CONSTRAINT fk_review_workflow_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_workflow_index_task
        FOREIGN KEY (index_task_id) REFERENCES index_task (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_workflow_review_task
        FOREIGN KEY (review_task_id) REFERENCES code_review_task (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_workflow_static_task
        FOREIGN KEY (static_analysis_task_id) REFERENCES static_analysis_task (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_workflow_embedding_task
        FOREIGN KEY (embedding_task_id) REFERENCES embedding_index_task (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_workflow_ai_task
        FOREIGN KEY (ai_review_task_id) REFERENCES ai_review_task (id) ON DELETE SET NULL
);

CREATE INDEX idx_review_workflow_project_created
    ON review_workflow_run (project_id, created_at);
