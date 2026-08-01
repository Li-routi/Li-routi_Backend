-- PR #82 이전 그룹의 초대코드 만료 시각을 보정하고 필수 제약을 적용한다(#109).

-- 기존 NULL 값은 배포 시점에 즉시 만료된 값으로 보정한다.
UPDATE member_group
   SET invite_code_expires_at = CURRENT_TIMESTAMP(6)
 WHERE invite_code_expires_at IS NULL;

ALTER TABLE `member_group`
  MODIFY COLUMN `invite_code_expires_at` datetime(6) NOT NULL;
