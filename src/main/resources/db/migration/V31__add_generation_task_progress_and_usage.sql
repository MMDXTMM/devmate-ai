CREATE TABLE generation_task (
 id BIGINT NOT NULL PRIMARY KEY,
 session_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 reference_id BIGINT NOT NULL,
 attempt_key VARCHAR(80) NOT NULL,
 status VARCHAR(24) NOT NULL,
 phase VARCHAR(100) NOT NULL,
 model_calls INT NOT NULL DEFAULT 0,
 input_tokens BIGINT NOT NULL DEFAULT 0,
 output_tokens BIGINT NOT NULL DEFAULT 0,
 usage_complete TINYINT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL,
 started_at TIMESTAMP NULL,
 finished_at TIMESTAMP NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uk_generation_task_attempt UNIQUE(kind,reference_id,attempt_key)
);
CREATE INDEX idx_generation_task_reference ON generation_task(session_id,kind,reference_id,created_at);
