-- V20260812062253__migrate_achievement_routine_category_filter_to_fk.sql 에서
-- achievement.routine_category_id 에 FK(REFERENCES routine_category(id))를 거는데,
-- 실제 routine_category(1~6) 시드는 R__seed_routine.sql 이 채운다.
--
-- Flyway는 항상 "모든 Versioned(V__) 마이그레이션 → 그 다음 Repeatable(R__) 마이그레이션"
-- 순서로 실행하므로, V__ 마이그레이션 시점에는 R__seed_routine.sql 이 아직 실행 전이라
-- routine_category 테이블이 비어 있을 수 있다. 이 상태에서 062253 이 FK를 걸면
-- 참조 무결성 검증에 걸려 SQLIntegrityConstraintViolationException 이 발생한다.
--
-- 이 파일은 그 FK 검증을 통과시키기 위해 062253 보다 먼저 실행되는 최소 seed 이다.
-- R__seed_routine.sql 은 수정하지 않는다 - 그 파일이 나중에 실행되면서 동일한
-- id(1~6)를 ON DUPLICATE KEY UPDATE 로 덮어써서 최종 값(name/color/display_order 등)을
-- 맞춘다. 여기서는 FK 제약을 만족시키는 데 필요한 최소 컬럼만 채운다.
--
-- 주의: R__seed_routine.sql 의 실제 시드 값(이름/순서 등)이 바뀌면 이 파일도 같이
-- 손댈 필요는 없다 - 여기는 "행이 존재한다"만 보장하면 된다. 다만 카테고리 개수(1~6)
-- 자체가 바뀌면(예: 카테고리 추가/삭제) 이 목록도 맞춰서 갱신해야 한다.
INSERT IGNORE INTO routine_category (id, member_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (1, NULL, '운동',     NULL, 1, TRUE, NOW(), NOW()),
  (2, NULL, '건강',     NULL, 2, TRUE, NOW(), NOW()),
  (3, NULL, '자기계발', NULL, 3, TRUE, NOW(), NOW()),
  (4, NULL, '생활정리', NULL, 4, TRUE, NOW(), NOW()),
  (5, NULL, '마음관리', NULL, 5, TRUE, NOW(), NOW()),
  (6, NULL, '취미',     NULL, 6, TRUE, NOW(), NOW());
