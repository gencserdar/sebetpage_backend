CREATE TABLE IF NOT EXISTS landing_paintings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visitor_id VARCHAR(64) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_landing_paintings_visitor (visitor_id),
    KEY idx_landing_paintings_created (created_at)
);
