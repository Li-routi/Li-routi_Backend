-- 개인 루틴의 수행 시작 시각.
-- 기존 루틴은 시작 시각을 알 수 없으므로 NULL을 유지한다.
ALTER TABLE `member_routine`
  ADD COLUMN `start_time` time DEFAULT NULL,
  ADD CONSTRAINT `ck_member_routine_time_range`
    CHECK ((`start_time` IS NULL OR `start_time` < `end_time`));
