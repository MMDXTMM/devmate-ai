CREATE TABLE code_review_feedback (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    finding_id BIGINT NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_code_review_feedback_finding UNIQUE (finding_id),
    CONSTRAINT fk_code_review_feedback_project
        FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_code_review_feedback_finding
        FOREIGN KEY (finding_id) REFERENCES review_finding (id) ON DELETE CASCADE
);

CREATE INDEX idx_code_review_feedback_project_type
    ON code_review_feedback (project_id, feedback_type);
