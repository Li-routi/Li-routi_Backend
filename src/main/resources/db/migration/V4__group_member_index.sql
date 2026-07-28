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
-- 부팅을 막지 않았고 아무도 몰랐다. 뒤집어 말하면 인덱스가 잘못돼 있어도 알려줄 장치가
-- 이 마이그레이션밖에 없다. 그래서 아래를 "있으면 통과"가 아니라 "정확히 같아야 통과"로 쓴다.
--
-- 세 경우로 갈린다.
--   없음        → 만든다
--   정확히 일치  → 건너뛴다 (운영이 여기에 해당한다)
--   이름은 같은데 정의가 다름 → 실패시킨다
--
-- 실패를 SIGNAL로 내지 못하는 이유: MySQL은 준비된 문(prepared statement)에서 SIGNAL을
-- 지원하지 않는다(ERROR 1295). 그래서 없는 컬럼을 참조해 오류를 낸다 — 컬럼 이름이 곧 메시지다.

-- 실제 정의를 "컬럼:순서:정렬:접두사길이:비유일" 형태로 직렬화한다.
-- 컬럼 구성뿐 아니라 순서(seq_in_index)·정렬 방향(collation)·접두사 인덱스 여부(sub_part)·
-- 유일성(non_unique)까지 담아야 "이름만 같은 다른 인덱스"를 걸러낼 수 있다.
SET @actual_definition := (
    SELECT GROUP_CONCAT(
               CONCAT(column_name, ':', seq_in_index, ':', IFNULL(collation, '-'),
                      ':', IFNULL(sub_part, '-'), ':', non_unique)
               ORDER BY seq_in_index SEPARATOR ','
           )
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'group_member'
       AND index_name = 'idx_group_member_group_status'
);

-- ADD INDEX `...` (`group_id`, `status`) 가 만드는 값이다.
SET @expected_definition := 'group_id:1:A:-:1,status:2:A:-:1';

SET @ddl := CASE
    WHEN @actual_definition IS NULL THEN
        'ALTER TABLE `group_member` ADD INDEX `idx_group_member_group_status` (`group_id`, `status`)'
    WHEN @actual_definition = @expected_definition THEN
        'DO 0'
    ELSE
        'SELECT `V4_FAILED__idx_group_member_group_status_exists_with_a_different_definition__drop_it_and_rerun`'
END;

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
