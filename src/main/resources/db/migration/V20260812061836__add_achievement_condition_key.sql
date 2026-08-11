-- 단일 조건 업적(NONE/CUMULATIVE_COUNT/DISTINCT_ROOM_COUNT)이 반응할 이벤트 키.
-- COMPOSITE 는 이 컬럼을 쓰지 않고 achievement_condition.condition_key 를 그대로 쓴다.
ALTER TABLE achievement
    ADD COLUMN condition_key VARCHAR(30) NULL AFTER target_count;

-- AchievementProgressEvent 처리 이력. 같은 원본 사건(source_type, source_id)에 대해
-- 이벤트가 재시도/중복 발행돼도 unique 제약으로 두 번째 반영을 막는다.
CREATE TABLE achievement_progress_event_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    condition_key VARCHAR(30) NOT NULL,     -- 이 사건이 반영된 conditionKey
    source_type   VARCHAR(30) NOT NULL,     -- 예: ROUTINE_CHECK, LIKE, POKE
    source_id     BIGINT      NOT NULL,     -- 원본 레코드 PK
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_achievement_progress_event_log UNIQUE (source_type, source_id, condition_key)
);
