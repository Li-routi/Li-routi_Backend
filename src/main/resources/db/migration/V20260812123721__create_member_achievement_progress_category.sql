-- CATEGORY_COVERAGE_COUNT(AC-008)의 "서로 다른 카테고리"를 세는 근거 테이블.
-- member_achievement_progress_day가 날짜 unique로 중복을 막는 것과 동일한 패턴을
-- 카테고리 축으로 적용한다.

CREATE TABLE member_achievement_progress_category
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_achievement_id   BIGINT   NOT NULL,
    routine_category_id     BIGINT   NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_achievement_progress_category
        UNIQUE (member_achievement_id, routine_category_id),
    CONSTRAINT fk_maproc_member_achievement
        FOREIGN KEY (member_achievement_id) REFERENCES member_achievement (id),
    CONSTRAINT fk_maproc_routine_category
        FOREIGN KEY (routine_category_id) REFERENCES routine_category (id)
);
