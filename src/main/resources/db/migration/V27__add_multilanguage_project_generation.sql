ALTER TABLE generation_spec_version ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE generation_spec_version ADD COLUMN ready_for_generation TINYINT NOT NULL DEFAULT 0;
ALTER TABLE generation_session ADD COLUMN last_error VARCHAR(500) NULL;

CREATE TABLE generation_run (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    attempt_key VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary MEDIUMTEXT NULL,
    run_instructions MEDIUMTEXT NULL,
    manifest_json MEDIUMTEXT NOT NULL,
    warnings_json MEDIUMTEXT NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_generation_run_session FOREIGN KEY (session_id) REFERENCES generation_session(id),
    CONSTRAINT fk_generation_run_version FOREIGN KEY (version_id) REFERENCES generation_spec_version(id),
    CONSTRAINT uk_generation_run_version UNIQUE (version_id)
);
CREATE INDEX idx_generation_run_session ON generation_run(session_id, created_at);
