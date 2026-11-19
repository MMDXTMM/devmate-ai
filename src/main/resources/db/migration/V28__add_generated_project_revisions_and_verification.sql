CREATE TABLE generation_revision (
 id BIGINT NOT NULL PRIMARY KEY,
 session_id BIGINT NOT NULL,
 run_id BIGINT NOT NULL,
 root_run_id BIGINT NULL,
 parent_id BIGINT NULL,
 attempt_key VARCHAR(80) NOT NULL,
 kind VARCHAR(24) NOT NULL,
 status VARCHAR(24) NOT NULL,
 request_text MEDIUMTEXT NULL,
 summary MEDIUMTEXT NULL,
 run_instructions MEDIUMTEXT NULL,
 manifest_json MEDIUMTEXT NOT NULL,
 warnings_json MEDIUMTEXT NOT NULL,
 error_message VARCHAR(500) NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uk_generation_revision_root UNIQUE(root_run_id),
 CONSTRAINT uk_generation_revision_attempt UNIQUE(run_id, attempt_key),
 CONSTRAINT fk_generation_revision_run FOREIGN KEY(run_id) REFERENCES generation_run(id)
);
CREATE INDEX idx_generation_revision_session ON generation_revision(session_id, created_at);
CREATE TABLE generation_verification (
 id BIGINT NOT NULL PRIMARY KEY,
 revision_id BIGINT NOT NULL,
 session_id BIGINT NOT NULL,
 attempt_key VARCHAR(80) NOT NULL,
 status VARCHAR(24) NOT NULL,
 profile VARCHAR(40) NULL,
 result_json MEDIUMTEXT NULL,
 running_key BIGINT NULL,
 repair_limit INT NOT NULL,
 repairs INT NOT NULL DEFAULT 0,
 result_revision_id BIGINT NULL,
 error_message VARCHAR(500) NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uk_generation_verification_running UNIQUE(running_key),
 CONSTRAINT uk_generation_verification_attempt UNIQUE(revision_id, attempt_key),
 CONSTRAINT fk_generation_verification_revision FOREIGN KEY(revision_id) REFERENCES generation_revision(id)
);
CREATE INDEX idx_generation_verification_session ON generation_verification(session_id, created_at);
