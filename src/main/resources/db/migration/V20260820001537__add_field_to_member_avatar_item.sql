ALTER TABLE member_avatar_item
    ADD COLUMN grant_reason VARCHAR(30) NULL COMMENT 'NULL=구매, ACHIEVEMENT_REWARD=업적 보상';
