-- 소프트 삭제된 그룹 루틴의 제목은 새 활성 루틴에서 재사용할 수 있어야 한다.
-- MySQL의 UNIQUE KEY는 NULL 값을 서로 다른 값으로 취급하므로, 비활성 행에는 NULL을 만들고
-- 활성 행에만 제목을 유지하는 생성 컬럼을 유니크 키에 사용한다.
ALTER TABLE `group_routine`
  DROP INDEX `uk_group_routine_group_title`,
  ADD COLUMN `active_title` varchar(20)
    GENERATED ALWAYS AS (IF(`active`, `title`, NULL)) STORED AFTER `active`,
  ADD UNIQUE KEY `uk_group_routine_group_title` (`group_id`, `active_title`);
