-- 그룹별 회원 활동 상태 (#162).
ALTER TABLE `group_member`
    ADD COLUMN `current_streak` int NOT NULL DEFAULT 0,
    ADD COLUMN `longest_streak` int NOT NULL DEFAULT 0,
    ADD COLUMN `last_streak_completed_date` date NULL,
    ADD COLUMN `total_like_count` bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT `ck_group_member_current_streak_non_negative`
        CHECK (`current_streak` >= 0),
    ADD CONSTRAINT `ck_group_member_longest_streak_non_negative`
        CHECK (`longest_streak` >= 0),
    ADD CONSTRAINT `ck_group_member_total_like_count_non_negative`
        CHECK (`total_like_count` >= 0);

-- 배포 시점에 ACTIVE인 현재 가입 회차의 Like만 누적값으로 초기화한다.
UPDATE `group_member` group_member
SET `total_like_count` = (
    SELECT COUNT(*)
    FROM `group_routine_verification_like` verification_like
    JOIN `group_routine_verification` verification
        ON verification.id = verification_like.group_routine_verification_id
    JOIN `group_routine_assignment` assignment
        ON assignment.id = verification.group_routine_assignment_id
    JOIN `group_routine` routine
        ON routine.id = assignment.group_routine_id
    WHERE assignment.member_id = group_member.member_id
      AND routine.group_id = group_member.group_id
      AND assignment.created_at >= group_member.joined_at
)
WHERE group_member.status = 'ACTIVE';
