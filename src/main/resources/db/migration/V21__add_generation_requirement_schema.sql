CREATE TABLE generation_session (
    id BIGINT NOT NULL PRIMARY KEY,
    owner_id BIGINT NULL,
    original_requirement VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latest_version_no INT NOT NULL DEFAULT 1,
    confirmed_version_id BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_generation_session_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

CREATE INDEX idx_generation_session_owner_created
    ON generation_session (owner_id, created_at);

CREATE INDEX idx_generation_session_status
    ON generation_session (status);

CREATE TABLE generation_spec_version (
    id BIGINT NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    requirement_summary MEDIUMTEXT NOT NULL,
    architecture_summary MEDIUMTEXT NOT NULL,
    assumptions_json MEDIUMTEXT NOT NULL,
    questions_json MEDIUMTEXT NOT NULL,
    answers_json MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_generation_spec_session
        FOREIGN KEY (session_id) REFERENCES generation_session (id),
    CONSTRAINT uk_generation_spec_version
        UNIQUE (session_id, version_no)
);

CREATE INDEX idx_generation_spec_session_created
    ON generation_spec_version (session_id, created_at);

ALTER TABLE generation_session
    ADD CONSTRAINT fk_generation_session_confirmed_version
        FOREIGN KEY (confirmed_version_id) REFERENCES generation_spec_version (id);
