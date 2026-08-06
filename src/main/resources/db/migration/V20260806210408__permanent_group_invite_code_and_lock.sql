-- 초대코드는 그룹에 영구 귀속하고, 신규 참여 제어용 잠금 상태를 추가한다(#145).
-- V13까지 적용된 기존 행은 아래 UPDATE로 모두 잠금 해제 상태로 보정한다.

ALTER TABLE `member_group`
    ADD COLUMN `is_locked` bit(1) NULL AFTER `invite_code`;

UPDATE `member_group`
   SET `is_locked` = b'0'
 WHERE `is_locked` IS NULL;

ALTER TABLE `member_group`
    DROP COLUMN `invite_code_expires_at`,
    MODIFY COLUMN `is_locked` bit(1) NOT NULL DEFAULT b'0';
