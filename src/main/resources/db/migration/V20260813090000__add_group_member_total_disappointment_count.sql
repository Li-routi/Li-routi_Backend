-- 현재 가입 회차에서 인증글이 받은 누적 아쉬워요 수 (#250).
ALTER TABLE `group_member`
    ADD COLUMN `total_disappointment_count` bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT `ck_group_member_total_disappointment_count_non_negative`
        CHECK (`total_disappointment_count` >= 0);

-- 기존 Like 누적값과 같은 기준으로 ACTIVE 가입 회차의 데이터만 backfill한다.
UPDATE `group_member` group_member
SET `total_disappointment_count` = (
    SELECT COUNT(*)
    FROM `group_routine_verification_disappointment` disappointment
    JOIN `group_routine_verification` verification
        ON verification.id = disappointment.group_routine_verification_id
    JOIN `group_routine_assignment` assignment
        ON assignment.id = verification.group_routine_assignment_id
    JOIN `group_routine` routine
        ON routine.id = assignment.group_routine_id
    WHERE assignment.member_id = group_member.member_id
      AND routine.group_id = group_member.group_id
      AND assignment.created_at >= group_member.joined_at
)
WHERE group_member.status = 'ACTIVE';
