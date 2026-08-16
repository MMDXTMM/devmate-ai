CREATE TABLE user_model_connection (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    is_active TINYINT NOT NULL DEFAULT 0,
    active_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_model_provider UNIQUE (user_id, provider),
    CONSTRAINT uk_user_active_model UNIQUE (active_user_id),
    CONSTRAINT fk_user_model_connection_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_model_active ON user_model_connection (user_id, is_active);
