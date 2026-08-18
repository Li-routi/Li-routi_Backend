-- 그룹 목록에서 인증 이력을 매번 집계하지 않도록 마지막 신규 인증 등록 시각을 그룹에 보관한다.
-- member_group은 Group 엔티티의 실제 테이블이며, group_member는 회원-그룹 관계 테이블이다.
ALTER TABLE `member_group`
    ADD COLUMN `last_verification_at` datetime(6) NULL;

-- verified_at은 재인증으로 바뀔 수 있으므로 저장 이력인 group_routine_verification.created_at을 기준으로 한다.
-- 인증 이력이 없는 그룹은 LEFT JOIN 결과가 NULL이어서 NULL을 그대로 유지한다.
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
SET group_entity.last_verification_at = latest.last_verification_at;
