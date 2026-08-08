-- 그룹별 상태 메시지는 회원 공통 프로필이 아니라 참여 관계에 보관한다.
ALTER TABLE `group_member`
    ADD COLUMN `status_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
