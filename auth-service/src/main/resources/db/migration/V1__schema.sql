CREATE TABLE IF NOT EXISTS credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    activated BIT NOT NULL,
    activation_code VARCHAR(64),
    activation_code_expires_at DATETIME(6),
    reset_code VARCHAR(64),
    reset_code_hash VARCHAR(128),
    reset_code_expires_at DATETIME(6),
    reset_code_attempts INT,
    pending_email_code VARCHAR(16),
    pending_email_new VARCHAR(254),
    pending_email_expires_at DATETIME(6),
    pending_email_attempts INT,
    pending_password_code VARCHAR(16),
    pending_password_new_hash VARCHAR(100),
    pending_password_expires_at DATETIME(6),
    pending_password_attempts INT,
    UNIQUE KEY uk_credentials_email (email),
    UNIQUE KEY uk_credentials_nickname (nickname)
);

CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    remember_me BIT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_sessions_token_hash (token_hash),
    KEY idx_sessions_user_id (user_id)
);
