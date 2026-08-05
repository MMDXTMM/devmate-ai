ALTER TABLE tool_call_log ADD COLUMN tool_call_id VARCHAR(128);
ALTER TABLE tool_call_log ADD COLUMN step_no INT;
ALTER TABLE tool_call_log ADD COLUMN arguments_hash CHAR(64);
ALTER TABLE tool_call_log ADD COLUMN error_code VARCHAR(64);

CREATE UNIQUE INDEX uk_tool_call_invocation_call
    ON tool_call_log (invocation_id, tool_call_id);
CREATE INDEX idx_tool_call_invocation_step
    ON tool_call_log (invocation_id, step_no);
