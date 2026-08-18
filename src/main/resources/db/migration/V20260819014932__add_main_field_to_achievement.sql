ALTER TABLE member
    ADD COLUMN representative_achievement_id BIGINT NULL,
ADD CONSTRAINT fk_member_representative_achievement
    FOREIGN KEY (representative_achievement_id) REFERENCES achievement (id);
