ALTER TABLE conversation ADD COLUMN revision VARCHAR(64);
ALTER TABLE conversation ADD COLUMN provider VARCHAR(32);
ALTER TABLE conversation ADD COLUMN model_name VARCHAR(100);
ALTER TABLE conversation ADD COLUMN prompt_version VARCHAR(64);

ALTER TABLE conversation_message ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE conversation_message ADD COLUMN attempt_key CHAR(36);
ALTER TABLE conversation_message ADD COLUMN request_hash CHAR(64);
ALTER TABLE conversation_message ADD COLUMN running_key BIGINT;
ALTER TABLE conversation_message ADD COLUMN evidence_json MEDIUMTEXT;
ALTER TABLE conversation_message ADD COLUMN error_code VARCHAR(100);
ALTER TABLE conversation_message ADD COLUMN error_message VARCHAR(500);
ALTER TABLE conversation_message ADD COLUMN latency_ms BIGINT;

ALTER TABLE conversation_message
    ADD CONSTRAINT uk_conversation_message_attempt UNIQUE (conversation_id, attempt_key);

ALTER TABLE conversation_message
    ADD CONSTRAINT uk_conversation_message_running UNIQUE (running_key);

CREATE INDEX idx_conversation_project_recent
    ON conversation (project_id, user_id, updated_at, id);
