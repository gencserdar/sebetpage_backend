CREATE TABLE IF NOT EXISTS communities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    is_private BIT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS community_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    role VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_community_members_pair (community_id, user_id),
    KEY idx_community_members_community (community_id),
    KEY idx_community_members_user (user_id)
);

CREATE TABLE IF NOT EXISTS community_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    KEY idx_community_invites_community (community_id),
    KEY idx_community_invites_to (to_user_id),
    KEY idx_community_invites_status (status)
);
