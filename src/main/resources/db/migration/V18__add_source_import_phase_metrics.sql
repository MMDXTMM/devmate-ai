ALTER TABLE index_task
    ADD COLUMN clone_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE index_task
    ADD COLUMN scan_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE index_task
    ADD COLUMN plan_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE index_task
    ADD COLUMN parse_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE index_task
    ADD COLUMN persist_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE index_task
    ADD COLUMN total_duration_ms BIGINT NOT NULL DEFAULT 0;
