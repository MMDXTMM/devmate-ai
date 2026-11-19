CREATE TABLE generation_insight (
 id BIGINT NOT NULL PRIMARY KEY,
 session_id BIGINT NOT NULL,
 revision_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 attempt_key VARCHAR(80) NOT NULL,
 running_key VARCHAR(80) NULL,
 status VARCHAR(24) NOT NULL,
 request_text MEDIUMTEXT NULL,
 output_json MEDIUMTEXT NULL,
 error_message VARCHAR(500) NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uk_generation_insight_attempt UNIQUE(revision_id,kind,attempt_key),
 CONSTRAINT uk_generation_insight_running UNIQUE(running_key),
 CONSTRAINT fk_generation_insight_revision FOREIGN KEY(revision_id) REFERENCES generation_revision(id)
);
CREATE INDEX idx_generation_insight_revision ON generation_insight(revision_id, kind, created_at);
