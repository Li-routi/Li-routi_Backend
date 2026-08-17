-- 기본 카테고리와 기존 그룹 루틴의 개인 카테고리를 그룹 전용 테이블로 이관한다(#109).
-- MySQL DDL은 자동 커밋되므로 컬럼 추가는 재실행 시 중복되지 않게 조건부로 수행한다.

-- repeatable migration보다 먼저 데이터 이관이 실행되므로 고정 id 1~6을 여기서 준비한다.
INSERT INTO group_routine_category
    (id, group_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (1, NULL, '운동',     NULL, 1, TRUE, NOW(6), NOW(6)),
  (2, NULL, '건강',     NULL, 2, TRUE, NOW(6), NOW(6)),
  (3, NULL, '자기계발', NULL, 3, TRUE, NOW(6), NOW(6)),
  (4, NULL, '생활정리', NULL, 4, TRUE, NOW(6), NOW(6)),
  (5, NULL, '마음관리', NULL, 5, TRUE, NOW(6), NOW(6)),
  (6, NULL, '취미',     NULL, 6, TRUE, NOW(6), NOW(6)) AS seeded
ON DUPLICATE KEY UPDATE
  name = seeded.name,
  display_order = seeded.display_order,
  active = seeded.active;

-- 사용 중인 개인 사용자 카테고리를 그룹별로 복제한다. 개인 카테고리 원본은 그대로 보존한다.
INSERT INTO group_routine_category
    (group_id, name, color, display_order, active, created_at, updated_at)
SELECT migrated.group_id,
       migrated.name,
       migrated.color,
       migrated.display_order,
       migrated.active,
       migrated.created_at,
       migrated.updated_at
  FROM (
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
       WHERE category.member_id IS NOT NULL
  ) AS migrated
ON DUPLICATE KEY UPDATE
  color = migrated.color,
  display_order = migrated.display_order,
  active = migrated.active,
  updated_at = migrated.updated_at;

-- 기존 FK 컬럼을 보존한 채 그룹 전용 FK 컬럼을 nullable로 추가한다.
SET @group_category_column_count := (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'group_routine'
       AND column_name = 'group_routine_category_id'
);
SET @column_ddl := CASE
    WHEN @group_category_column_count = 0 THEN
        'ALTER TABLE `group_routine` ADD COLUMN `group_routine_category_id` bigint NULL AFTER `category_id`'
    ELSE 'DO 0'
END;
PREPARE stmt FROM @column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 고정 카테고리는 새 테이블에서도 id 1~6을 사용한다.
UPDATE group_routine routine
JOIN routine_category old_category
  ON old_category.id = routine.category_id
 AND old_category.member_id IS NULL
JOIN group_routine_category new_category
  ON new_category.id = old_category.id
SET routine.group_routine_category_id = new_category.id
WHERE routine.group_routine_category_id IS NULL;

-- 사용자 카테고리는 그룹별로 복제한 행에 연결한다.
UPDATE group_routine routine
JOIN routine_category old_category
  ON old_category.id = routine.category_id
 AND old_category.member_id IS NOT NULL
JOIN group_routine_category new_category
  ON new_category.group_id = routine.group_id
 AND new_category.name = old_category.name
SET routine.group_routine_category_id = new_category.id
WHERE routine.group_routine_category_id IS NULL;

-- 모든 루틴이 고정 카테고리 또는 자기 그룹의 카테고리에 연결됐는지 검증한다.
SET @unmapped_routine_count := (
    SELECT COUNT(*)
      FROM group_routine routine
      LEFT JOIN group_routine_category category
        ON category.id = routine.group_routine_category_id
       AND (category.group_id IS NULL OR category.group_id = routine.group_id)
     WHERE category.id IS NULL
);
SET @validation_sql := CASE
    WHEN @unmapped_routine_count = 0 THEN 'DO 0'
    ELSE 'SELECT `V11_FAILED__group_routine_category_mapping_is_incomplete`'
END;
PREPARE stmt FROM @validation_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
