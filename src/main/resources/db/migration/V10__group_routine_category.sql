-- 그룹 루틴 카테고리를 개인 루틴 카테고리에서 분리한다(#109).
--
-- 기존 category_id와 routine_category FK는 이번 전환에서 제거하지 않는다.
-- V11에서 별도의 group_routine_category_id를 채우고 V12에서 필수 제약을 적용한 뒤,
-- 운영 검증이 끝나면 후속 마이그레이션에서 기존 컬럼을 제거한다.
-- 데이터 충돌 검증은 DDL보다 먼저 실행해 예상 가능한 실패가 스키마를 바꾸지 않게 한다.

-- group_routine이 참조하는 고정 카테고리는 R__seed_routine.sql의 id·이름과 정확히 같아야 한다.
SET @unexpected_fixed_category_count := (
    SELECT COUNT(*)
      FROM group_routine routine
      JOIN routine_category category ON category.id = routine.category_id
     WHERE category.member_id IS NULL
       AND NOT (
           (category.id = 1 AND category.name = '운동')
        OR (category.id = 2 AND category.name = '건강')
        OR (category.id = 3 AND category.name = '자기계발')
        OR (category.id = 4 AND category.name = '생활정리')
        OR (category.id = 5 AND category.name = '마음관리')
        OR (category.id = 6 AND category.name = '취미')
       )
);
SET @validation_sql := CASE
    WHEN @unexpected_fixed_category_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V10_FAILED__group_routine_references_an_unexpected_fixed_category`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 개인 사용자 카테고리 이름이 그룹 기본 카테고리와 겹치면 어느 쪽도 임의 변경하지 않는다.
SET @fixed_name_conflict_count := (
    SELECT COUNT(*)
      FROM group_routine routine
      JOIN routine_category category ON category.id = routine.category_id
     WHERE category.member_id IS NOT NULL
       AND category.name IN ('운동', '건강', '자기계발', '생활정리', '마음관리', '취미')
);
SET @validation_sql := CASE
    WHEN @fixed_name_conflict_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V10_FAILED__custom_category_name_conflicts_with_a_fixed_category`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 같은 그룹에서 서로 다른 개인 카테고리가 같은 이름으로 사용 중이면 한 행으로 합치지 않고 실패한다.
SET @same_group_name_conflict_count := (
    SELECT COUNT(*)
      FROM (
          SELECT routine.group_id, category.name
            FROM group_routine routine
            JOIN routine_category category ON category.id = routine.category_id
           WHERE category.member_id IS NOT NULL
           GROUP BY routine.group_id, category.name
          HAVING COUNT(DISTINCT category.id) > 1
      ) conflicts
);
SET @validation_sql := CASE
    WHEN @same_group_name_conflict_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V10_FAILED__same_group_has_distinct_custom_categories_with_the_same_name`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 신규 테이블이 이미 존재하면 예상하지 않은 스키마 드리프트이므로 즉시 실패한다.
CREATE TABLE `group_routine_category` (
  `active` bit(1) NOT NULL,
  `display_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `color` enum('BLACK','BLUE','GREEN','MAGENTA','ORANGE','RED','YELLOW') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_category_group_name` (`group_id`,`name`),
  KEY `idx_group_routine_category_group_active` (`group_id`,`active`),
  CONSTRAINT `FK_group_routine_category_group`
      FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
