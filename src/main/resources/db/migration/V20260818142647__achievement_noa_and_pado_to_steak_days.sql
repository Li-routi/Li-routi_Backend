-- 노아: 기존 EG-003을 STREAK_DAYS로 재정의
UPDATE achievement
SET progress_type = 'STREAK_DAYS',
    condition_key = 'MORNING_ROUTINE_STREAK_DAYS',
    target_count = 7,
    condition_desc = '기상·아침 성격의 루틴을 7일 연속 완료'
WHERE code = 'ACH-EG-003';

-- 회원별 "기상 루틴" 연속 기록
CREATE TABLE member_morning_routine_streak
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id                   BIGINT   NOT NULL,
    current_streak              INT      NOT NULL DEFAULT 0,
    last_streak_completed_date  DATE     NULL,
    version                     BIGINT   NOT NULL DEFAULT 0,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_morning_routine_streak_member UNIQUE (member_id),
    CONSTRAINT fk_member_morning_routine_streak_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- 파도 전용 신규 업적
INSERT INTO achievement
(code, category, name, condition_desc, progress_type, target_count, condition_key, topaz_reward, badge_yn, limited_outfit_yn, sort_order, hidden_yn, active)
VALUES
    ('ACH-EG-013', 'EGG', '파도의 도전', '사용자가 선택한 개인 루틴 하나를 예정일마다 10회 연속 완료',
     'STREAK_DAYS', 10, 'WAVE_ROUTINE_STREAK_DAYS', 0, FALSE, FALSE, 48, FALSE, TRUE);

-- 회원이 선택한 파도 추적 루틴 + 연속 기록
CREATE TABLE member_wave_routine_streak
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id                   BIGINT   NOT NULL,
    member_routine_id           BIGINT   NOT NULL,
    current_streak              INT      NOT NULL DEFAULT 0,
    last_streak_completed_date  DATE     NULL,
    version                     BIGINT   NOT NULL DEFAULT 0,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_wave_routine_streak_member UNIQUE (member_id),
    CONSTRAINT fk_member_wave_routine_streak_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_member_wave_routine_streak_routine FOREIGN KEY (member_routine_id) REFERENCES member_routine (id)
);

-- 캐릭터 언락 조건: 노아는 그대로(EG-003), 파도만 새 업적으로 갱신
UPDATE character_unlock_condition
SET condition_param = 'ACH-EG-013'
WHERE condition_param = 'ACH-EG-006'
  AND character_id = (SELECT id FROM avatar_character WHERE name = '파도');
