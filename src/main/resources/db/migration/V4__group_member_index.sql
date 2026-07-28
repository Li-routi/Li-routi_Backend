-- group_member 조회 인덱스를 마이그레이션에 편입한다(#71).
--
-- GroupMember 엔티티가 @Index(idx_group_member_group_status, "group_id, status")를 선언하는데
-- V1__baseline.sql에는 이 인덱스가 없다. GroupMemberRepository.findAllByGroupIdAndStatus가
-- 쓰는 조합이라 실제로 필요한 인덱스다.
--
-- 왜 어긋났나:
--   운영 docker-compose.yml에 JPA_DDL_AUTO=update가 하드코딩돼 있어(Flyway 도입 전 수동 복사본)
--   Hibernate가 엔티티의 @Index를 보고 운영에만 조용히 인덱스를 만들었다. 마이그레이션에는
--   그 사실이 남지 않아 로컬·CI에는 없는 상태가 됐다. (2026-07-28에 validate로 되돌렸다)
--
-- ddl-auto: validate는 테이블·컬럼만 검사하고 인덱스는 보지 않는다. 그래서 이 불일치는
-- 부팅을 막지 않았고 아무도 몰랐다.
--
-- 조건부로 쓰는 이유:
--   운영에는 이미 인덱스가 있어서 그냥 ADD INDEX 하면 Duplicate key name으로 배포가 깨진다.
--   MySQL에는 CREATE INDEX IF NOT EXISTS가 없으므로 information_schema로 존재를 확인한다.
SET @index_exists := (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'group_member'
       AND index_name = 'idx_group_member_group_status'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `group_member` ADD INDEX `idx_group_member_group_status` (`group_id`, `status`)',
    'DO 0'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
