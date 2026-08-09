-- 그룹 구성원 쿡쿡 찌르기 누적 수 (#179). 별도 이력 없이 현재 가입 회차의 수치만 유지한다.
ALTER TABLE `group_member`
    ADD COLUMN `total_poke_count` bigint NOT NULL DEFAULT 0;
