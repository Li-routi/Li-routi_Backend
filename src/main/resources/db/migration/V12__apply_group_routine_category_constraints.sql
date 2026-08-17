-- 이관이 완료된 그룹 루틴 카테고리 FK를 필수 제약으로 전환한다(#109).

SET @group_category_fk_count := (
    SELECT COUNT(*)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'group_routine'
       AND constraint_name = 'FK_group_routine_category'
);
SET @fk_ddl := CASE
    WHEN @group_category_fk_count = 0 THEN
        'ALTER TABLE `group_routine` ADD CONSTRAINT `FK_group_routine_category` FOREIGN KEY (`group_routine_category_id`) REFERENCES `group_routine_category` (`id`)'
    ELSE 'DO 0'
END;
PREPARE stmt FROM @fk_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 신규 애플리케이션은 그룹 전용 컬럼을 필수로 사용한다.
ALTER TABLE `group_routine`
  MODIFY COLUMN `group_routine_category_id` bigint NOT NULL;

-- 기존 컬럼과 개인 카테고리 FK는 한 배포 동안 유지한다. 신규 저장은 이 컬럼을 사용하지 않는다.
ALTER TABLE `group_routine`
  MODIFY COLUMN `category_id` bigint NULL;
