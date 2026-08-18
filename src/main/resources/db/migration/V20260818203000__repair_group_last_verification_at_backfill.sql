-- V20260818194500 적용 중 신규 버전 인증 저장과 backfill이 겹쳐도 더 최신 값을 덮어쓰지 않는다.
-- 구버전 인증 쓰기는 배포 절차에서 drain 또는 차단한 뒤 이 repair를 적용해야 한다.
UPDATE `member_group` group_entity
LEFT JOIN (
    SELECT routine.group_id, MAX(verification.created_at) AS last_verification_at
    FROM `group_routine_verification` verification
    JOIN `group_routine_assignment` assignment
        ON assignment.id = verification.group_routine_assignment_id
    JOIN `group_routine` routine
        ON routine.id = assignment.group_routine_id
    GROUP BY routine.group_id
) latest
    ON latest.group_id = group_entity.id
SET group_entity.last_verification_at = CASE
    WHEN latest.last_verification_at IS NOT NULL
         AND (
             group_entity.last_verification_at IS NULL
             OR latest.last_verification_at > group_entity.last_verification_at
         )
    THEN latest.last_verification_at
    ELSE group_entity.last_verification_at
END;
