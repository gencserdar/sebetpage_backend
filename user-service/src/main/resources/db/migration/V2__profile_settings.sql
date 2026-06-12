ALTER TABLE user_profiles
    ADD COLUMN bio VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN social_links_json TEXT NULL,
    ADD COLUMN profile_card_json TEXT NULL;

UPDATE user_profiles
SET social_links_json = '[]'
WHERE social_links_json IS NULL;

UPDATE user_profiles
SET profile_card_json = '{"widgets":[]}'
WHERE profile_card_json IS NULL;

ALTER TABLE user_profiles
    MODIFY COLUMN social_links_json TEXT NOT NULL,
    MODIFY COLUMN profile_card_json TEXT NOT NULL;
