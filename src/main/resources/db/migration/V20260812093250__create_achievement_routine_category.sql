CREATE TABLE achievement_routine_category
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    achievement_id        BIGINT NOT NULL,
    routine_category_id   BIGINT NOT NULL,
    CONSTRAINT uk_achievement_routine_category UNIQUE (achievement_id, routine_category_id),
    CONSTRAINT fk_arc_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id),
    CONSTRAINT fk_arc_routine_category FOREIGN KEY (routine_category_id) REFERENCES routine_category (id)
);
