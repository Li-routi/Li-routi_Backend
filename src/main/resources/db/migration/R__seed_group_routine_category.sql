-- 그룹 루틴의 기본 제공 카테고리. 개인 routine_category와 같은 목록을 별도 테이블에서 관리한다.
-- 고정 id를 유지하고 repeatable migration 재실행 시 기획 값을 복원한다.

INSERT INTO group_routine_category
    (id, group_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (1, NULL, '운동',     NULL, 1, TRUE, NOW(6), NOW(6)),
  (2, NULL, '건강',     NULL, 2, TRUE, NOW(6), NOW(6)),
  (3, NULL, '자기계발', NULL, 3, TRUE, NOW(6), NOW(6)),
  (4, NULL, '생활정리', NULL, 4, TRUE, NOW(6), NOW(6)),
  (5, NULL, '마음관리', NULL, 5, TRUE, NOW(6), NOW(6)),
  (6, NULL, '취미',     NULL, 6, TRUE, NOW(6), NOW(6))
AS new_row
ON DUPLICATE KEY UPDATE
  group_id      = new_row.group_id,
  name          = new_row.name,
  color         = new_row.color,
  display_order = new_row.display_order,
  active        = new_row.active,
  updated_at    = NOW(6);
