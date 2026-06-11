CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    user_a_id BIGINT,
    user_b_id BIGINT,
    title VARCHAR(255),
    description VARCHAR(1024),
    image_url VARCHAR(1024),
    created_by_id BIGINT,
    deleted_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_conversations_direct (type, user_a_id, user_b_id)
);

CREATE TABLE IF NOT EXISTS conversation_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    last_read_at DATETIME(6),
    deleted_at DATETIME(6),
    muted BIT,
    pinned BIT,
    role VARCHAR(255),
    can_change_photo BIT,
    can_change_description BIT,
    can_change_name BIT,
    can_remove_members BIT,
    can_add_members BIT,
    UNIQUE KEY uk_participants_conv_user (conversation_id, user_id),
    KEY idx_participants_user (user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content_cipher_b64 VARCHAR(4096) NOT NULL,
    content_iv_b64 VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_messages_conversation_created (conversation_id, created_at)
);
