ALTER TABLE ai_review_task
    ADD COLUMN attempt_key VARCHAR(36);

CREATE UNIQUE INDEX uk_ai_review_attempt_key
    ON ai_review_task (attempt_key);
