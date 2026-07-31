CREATE TABLE project (
    id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    source_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    source_location VARCHAR(1000),
    default_branch VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_project_status ON project (status);
CREATE INDEX idx_project_created_at ON project (created_at);

