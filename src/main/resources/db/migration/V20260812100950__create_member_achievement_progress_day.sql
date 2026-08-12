-- DISTINCT_DAY_COUNT · WEEKLY_DISTINCT_DAY_COUNT · MONTHLY_DISTINCT_DAY_COUNT progress_type이
-- "서로 다른 날짜"를 세는 근거 테이블. 회원-업적-날짜 조합당 최대 한 행만 남는다.
-- (member_achievement_id, progress_date) unique 제약이 "같은 날 중복 카운트 방지"의 핵심이다 -
-- MemberAchievementProgressDayService 가 insert 실패(제약 위반)를 "오늘은 이미 세었음" 신호로 쓴다.
--
-- updated_at 이 없는 이유: 이 테이블은 append-only 마킹 로그라 행이 생성된 뒤 절대 수정되지 않는다
-- (다른 엔티티들이 상속하는 BaseEntity 의 감사 컬럼 패턴을 따르지 않고 최소 컬럼만 둔다).
CREATE TABLE member_achievement_progress_day
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_achievement_id   BIGINT   NOT NULL,
    progress_date           DATE     NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_achievement_progress_day UNIQUE (member_achievement_id, progress_date),
    CONSTRAINT fk_member_achievement_progress_day_member_achievement
        FOREIGN KEY (member_achievement_id) REFERENCES member_achievement (id)
);

ALTER TABLE achievement DROP FOREIGN KEY fk_achievement_routine_category;
ALTER TABLE achievement DROP COLUMN routine_category_id;
