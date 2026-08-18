-- AC-009(GROUP_CUMULATIVE_COUNT), SP-004(GROUP_DISTINCT_DAY_COUNT)가
-- condition_key 대신 group_id 단위로 판정된다고 명시된 주석(align_achievement_latest_policy.sql)의
-- 근거 테이블. 이제껏 코드에서만 참조되고 실제 생성분은 없었다.

CREATE TABLE group_achievement_progress
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id          BIGINT   NOT NULL,
    achievement_id    BIGINT   NOT NULL,
    current_progress  INT      NOT NULL DEFAULT 0,
    achieved_at       DATETIME NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_achievement_progress UNIQUE (group_id, achievement_id),
    CONSTRAINT fk_gap_group FOREIGN KEY (group_id) REFERENCES member_group (id),
    CONSTRAINT fk_gap_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id)
);

-- (achievement_id, source_type, source_id) 단위 멱등 - 같은 사건이 재발행돼도
-- group_achievement_progress.current_progress를 두 번 올리지 않게 막는다.
CREATE TABLE group_achievement_progress_event_log
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id       BIGINT      NOT NULL,
    achievement_id BIGINT      NOT NULL,
    source_type    VARCHAR(30) NOT NULL,
    source_id      BIGINT      NOT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_achievement_progress_event_log
        UNIQUE (achievement_id, source_type, source_id)
);
