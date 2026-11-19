ALTER TABLE generation_revision ADD COLUMN running_key BIGINT NULL;
ALTER TABLE generation_revision ADD CONSTRAINT uk_generation_revision_running UNIQUE(running_key);
CREATE TABLE generation_change_plan (
 id BIGINT NOT NULL PRIMARY KEY,
 session_id BIGINT NOT NULL,
 revision_id BIGINT NOT NULL,
 attempt_key VARCHAR(80) NOT NULL,
 running_key BIGINT NULL,
 request_text VARCHAR(2000) NOT NULL,
 status VARCHAR(24) NOT NULL,
 plan_json MEDIUMTEXT NULL,
 result_revision_id BIGINT NULL,
 error_message VARCHAR(500) NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uk_generation_plan_attempt UNIQUE(revision_id,attempt_key),
 CONSTRAINT uk_generation_plan_running UNIQUE(running_key),
 CONSTRAINT fk_generation_plan_revision FOREIGN KEY(revision_id) REFERENCES generation_revision(id)
);
CREATE TABLE generation_selection (
 run_id BIGINT NOT NULL PRIMARY KEY,
 revision_id BIGINT NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT fk_generation_selection_run FOREIGN KEY(run_id) REFERENCES generation_run(id),
 CONSTRAINT fk_generation_selection_revision FOREIGN KEY(revision_id) REFERENCES generation_revision(id)
);
