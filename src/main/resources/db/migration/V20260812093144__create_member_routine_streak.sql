-- '100일 완주'(스페셜)와 '100일의 태양'(캐릭터 알)이 참조하는 연속기록은
-- 그룹 멤버십 단위(GroupMember.currentStreak)가 아니라, 개인 루틴이든 그룹
-- 루틴이든 하루에 하나라도 완료하면 이어지는 회원 전체 기준 연속기록이다.
-- GroupMember의 스트릭 필드 3종(currentStreak/longestStreak/
-- lastStreakCompletedDate)과 동일한 패턴을 회원 단위로 별도 관리한다.
CREATE TABLE member_routine_streak
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id                   BIGINT   NOT NULL,
    current_streak              INT      NOT NULL DEFAULT 0,
    longest_streak              INT      NOT NULL DEFAULT 0,
    last_streak_completed_date  DATE     NULL,
    version                     BIGINT   NOT NULL DEFAULT 0,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_routine_streak_member UNIQUE (member_id),
    CONSTRAINT fk_member_routine_streak_member FOREIGN KEY (member_id) REFERENCES member (id)
);
