-- 그룹 초대코드 말소 시각을 저장한다.

ALTER TABLE `member_group`
    ADD COLUMN `invite_code_expires_at` datetime(6) NULL AFTER `invite_code`;
