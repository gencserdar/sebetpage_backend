CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    surname VARCHAR(255),
    profile_image_url VARCHAR(512),
    UNIQUE KEY uk_user_profiles_email (email),
    UNIQUE KEY uk_user_profiles_nickname (nickname)
);

CREATE TABLE IF NOT EXISTS friendships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_friendships_users (user1_id, user2_id),
    KEY idx_friendships_user1 (user1_id),
    KEY idx_friendships_user2 (user2_id)
);

CREATE TABLE IF NOT EXISTS friend_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    KEY idx_friend_requests_from (from_user_id),
    KEY idx_friend_requests_to (to_user_id),
    KEY idx_friend_requests_status (status)
);

CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_blocks_pair (blocker_id, blocked_id),
    KEY idx_user_blocks_blocker (blocker_id),
    KEY idx_user_blocks_blocked (blocked_id)
);
