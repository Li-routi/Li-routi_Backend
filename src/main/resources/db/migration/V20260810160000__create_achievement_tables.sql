-- 업적 마스터
CREATE TABLE achievement
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                    VARCHAR(20)  NOT NULL,                 -- ACH-ST-001 등 기획 문서 ID 그대로 사용
    category                VARCHAR(10)  NOT NULL,                 -- START / ACHIEVE / SPECIAL
    name                    VARCHAR(50)  NOT NULL,
    condition_desc          VARCHAR(200) NOT NULL,
    progress_type           VARCHAR(30)  NOT NULL,                 -- NONE / CUMULATIVE_COUNT / DISTINCT_ROOM_COUNT / COMPOSITE
    target_count            INT          NULL,                     -- 단일 조건 업적만 사용, COMPOSITE는 NULL
    routine_category_filter VARCHAR(20)  NULL,                     -- 카테고리 시작 업적만 사용 (EXERCISE/HEALTH/SELF_DEVELOPMENT/ORGANIZE/MIND/HOBBY)
    topaz_reward            INT          NOT NULL,
    badge_yn                BOOLEAN      NOT NULL DEFAULT FALSE,
    limited_outfit_yn       BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order              INT          NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_achievement_code UNIQUE (code)
);

-- 복합 조건 업적의 개별 조건 정의 (현재는 SP-001만 해당, 확장 대비 별도 테이블로 분리)
CREATE TABLE achievement_condition
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    achievement_id BIGINT      NOT NULL,
    condition_key  VARCHAR(30) NOT NULL,     -- 예: POKE_COUNT, LIKE_COUNT
    target_count   INT         NOT NULL,
    sort_order     INT         NOT NULL,
    CONSTRAINT fk_achievement_condition_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievement(id),
    CONSTRAINT uk_achievement_condition UNIQUE (achievement_id, condition_key)
);

-- 보상 부가 항목 (배지/한정 의상 등 아이템명, 추후 아이템 마스터 연동 시 item_ref_id로 확장)
CREATE TABLE achievement_reward_item
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    achievement_id BIGINT      NOT NULL,
    item_category  VARCHAR(20) NOT NULL,     -- BADGE / OUTFIT
    item_name      VARCHAR(50) NOT NULL,
    item_ref_id    BIGINT      NULL,
    CONSTRAINT fk_reward_item_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievement(id)
);

-- 회원별 업적 진행/달성 현황
CREATE TABLE member_achievement
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT      NOT NULL,
    achievement_id   BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS / ACHIEVED / CLAIMED
    current_progress INT         NOT NULL DEFAULT 0,               -- 단일 조건 업적만 사용
    achieved_at      DATETIME    NULL,
    claimed_at       DATETIME    NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_achievement_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievement(id),
    CONSTRAINT uk_member_achievement UNIQUE (member_id, achievement_id)  -- 계정당 1회 달성 보장
);

-- 회원별 복합 조건 진행도 (SP-001 등)
CREATE TABLE member_achievement_condition
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_achievement_id BIGINT      NOT NULL,
    condition_key         VARCHAR(30) NOT NULL,
    current_value         INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_condition_member_achievement
        FOREIGN KEY (member_achievement_id) REFERENCES member_achievement(id),
    CONSTRAINT uk_member_achievement_condition UNIQUE (member_achievement_id, condition_key)
);
