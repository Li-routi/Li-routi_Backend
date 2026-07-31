-- 그룹 루틴 카테고리를 개인 루틴 카테고리에서 분리한다(#109).
--
-- 기존 group_routine 행은 삭제하지 않는다. 고정 카테고리는 같은 id로 옮기고,
-- 개인 사용자 카테고리는 실제로 사용 중인 그룹마다 복제한 뒤 category_id만 새 테이블로 연결한다.
-- 이름 충돌이나 예상하지 못한 고정 카테고리가 있으면 값을 임의로 바꾸지 않고 마이그레이션을 실패시킨다.

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

-- V10의 데이터 이관은 repeatable migration보다 먼저 실행되므로 기본 행을 여기서도 준비한다.
-- 이후 배포에서는 R__seed_group_routine_category.sql이 같은 고정 id를 계속 관리한다.
INSERT INTO group_routine_category
    (id, group_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (1, NULL, '운동',     NULL, 1, TRUE, NOW(6), NOW(6)),
  (2, NULL, '건강',     NULL, 2, TRUE, NOW(6), NOW(6)),
  (3, NULL, '자기계발', NULL, 3, TRUE, NOW(6), NOW(6)),
  (4, NULL, '생활정리', NULL, 4, TRUE, NOW(6), NOW(6)),
  (5, NULL, '마음관리', NULL, 5, TRUE, NOW(6), NOW(6)),
  (6, NULL, '취미',     NULL, 6, TRUE, NOW(6), NOW(6));

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

-- 사용 중인 개인 사용자 카테고리를 그룹별로 복제한다. 개인 카테고리 원본은 그대로 보존한다.
INSERT INTO group_routine_category
    (group_id, name, color, display_order, active, created_at, updated_at)
SELECT DISTINCT
       routine.group_id,
       category.name,
       category.color,
       category.display_order,
       category.active,
       category.created_at,
       category.updated_at
  FROM group_routine routine
  JOIN routine_category category ON category.id = routine.category_id
 WHERE category.member_id IS NOT NULL;

-- FK를 제거하기 전에 모든 사용자 카테고리의 복제 행이 준비됐는지 한 번 더 확인한다.
SET @missing_custom_mapping_count := (
    SELECT COUNT(*)
      FROM group_routine routine
      JOIN routine_category old_category
        ON old_category.id = routine.category_id
       AND old_category.member_id IS NOT NULL
      LEFT JOIN group_routine_category new_category
        ON new_category.group_id = routine.group_id
       AND new_category.name = old_category.name
     WHERE new_category.id IS NULL
);
SET @validation_sql := CASE
    WHEN @missing_custom_mapping_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V10_FAILED__custom_category_mapping_was_not_prepared`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 기존 FK를 제거한 뒤 사용자 카테고리 참조만 그룹별 복제 행의 id로 바꾼다.
-- 고정 카테고리는 새 테이블에서도 id 1~6을 사용하므로 category_id를 그대로 유지한다.
ALTER TABLE `group_routine`
  DROP FOREIGN KEY `FK9bqfxl6bqtl19pgar0j1rcog8`;

UPDATE group_routine routine
JOIN routine_category old_category
  ON old_category.id = routine.category_id
 AND old_category.member_id IS NOT NULL
JOIN group_routine_category new_category
  ON new_category.group_id = routine.group_id
 AND new_category.name = old_category.name
SET routine.category_id = new_category.id;

-- 모든 루틴이 고정 카테고리 또는 자기 그룹의 카테고리에 연결됐는지 확인한다.
SET @unmapped_routine_count := (
    SELECT COUNT(*)
      FROM group_routine routine
      LEFT JOIN group_routine_category category
        ON category.id = routine.category_id
       AND (category.group_id IS NULL OR category.group_id = routine.group_id)
     WHERE category.id IS NULL
);
SET @validation_sql := CASE
    WHEN @unmapped_routine_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V10_FAILED__group_routine_category_mapping_is_incomplete`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `group_routine`
  ADD CONSTRAINT `FK_group_routine_category`
      FOREIGN KEY (`category_id`) REFERENCES `group_routine_category` (`id`);

-- PR #82 이전 그룹은 만료 시각이 NULL일 수 있다. 배포 시점에 즉시 만료된 값으로 보정한다.
UPDATE member_group
   SET invite_code_expires_at = CURRENT_TIMESTAMP(6)
 WHERE invite_code_expires_at IS NULL;

ALTER TABLE `member_group`
  MODIFY COLUMN `invite_code_expires_at` datetime(6) NOT NULL;
