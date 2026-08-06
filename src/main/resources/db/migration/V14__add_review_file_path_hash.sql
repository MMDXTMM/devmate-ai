ALTER TABLE code_review_file
    ADD COLUMN new_path_hash VARCHAR(64);

CREATE INDEX idx_code_review_file_task_path_hash
    ON code_review_file (review_task_id, new_path_hash);
