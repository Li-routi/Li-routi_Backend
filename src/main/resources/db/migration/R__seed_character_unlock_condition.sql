-- 캐릭터 해금 조건 시드.
--
-- 파일 이름이 R__seed_character 뒤에 오도록 지었다. 반복 마이그레이션은 이름순으로 돌고,
-- 이 표는 avatar_character 를 외래 키로 참조하므로 마스터가 먼저 들어가야 한다.
--
-- id 는 시드가 직접 지정한다. 이 표에는 자연 키가 없어서(같은 캐릭터에 같은 키를 여러 번 걸
-- 수도 있다) id 로 걸지 않으면 매 배포마다 행이 쌓인다.
--
-- 해금은 업적으로만 한다.
--
-- 캐릭터 열둘은 저마다 EGG 업적 하나의 보상이다. 사용자가 그 업적을 달성하고 "받기" 를 누르면
-- AchievementClaimService 가 CharacterUnlockService.unlockByAchievementClaim 을 부르고, 그것이
-- 여기 ACHIEVEMENT_CLAIMED 행을 찾아 캐릭터를 넣는다. condition_param 이 업적 코드다.
--
-- ⚠️ 예전에는 캐릭터마다 직접 조건(ACTIVE_DAYS · CATEGORY_DAYS · TIME_WINDOW_DAYS …)을 걸었다.
--    그 결과 한 캐릭터를 여는 길이 둘이 되었고, 조건 쪽이 먼저 열어 업적의 "받기" 가 눌러도
--    아무 일도 일어나지 않는 상태가 됐다. 실제로 민트가 ACTIVE_DAYS=1 이라 인증 한 번이면
--    열려서, 회원 전원이 업적을 받기도 전에 민트를 갖고 있었다.
--
--    업적 조건이 기획의 단일 진실 공급원이므로 직접 조건을 전부 걷어내고 업적에 붙인다.
--    난이도는 achievement.condition_key / target_count 가 정한다 — 여기서 다시 정하지 않는다.
--
-- ⚠️ 루티(1)에는 조건이 없다. 행이 하나도 없으면 "항상 열려 있는 기본 캐릭터" 라는 뜻이고,
--    가입 시 기본 지급이 그 규칙으로 표현된다. 여기에 루티를 넣으면 안 된다.
--
-- 판정기 여덟(ACTIVE_DAYS · CATEGORY_DAYS · TIME_WINDOW_DAYS · ALL_KINDS_DAYS · ROUTINE_STREAK ·
-- STREAK_DAYS · LIKE_GIVEN · DEADLINE_RUSH)은 코드에 남아 있지만 캐릭터 해금에서는 쓰이지
-- 않는다. 지우지 않은 것은 조건을 다시 데이터로 걸고 싶어질 때를 위해서다.

INSERT INTO `character_unlock_condition` (`id`, `character_id`, `condition_key`, `condition_param`,
                                          `target_count`, `sort_order`, `created_at`, `updated_at`)
VALUES
    -- 노아 — 일찍 일어난 새
    (1, 2, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-003', 1, 1, NOW(6), NOW(6)),
    -- 모리 — 자정의 방문자
    (2, 3, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-009', 1, 1, NOW(6), NOW(6)),
    -- 코코 — 건강한 땀방울
    (3, 4, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-004', 1, 1, NOW(6), NOW(6)),
    -- 유키 — 마음에 쉼표
    (4, 5, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-005', 1, 1, NOW(6), NOW(6)),
    -- 민트 — 첫 루틴의 새싹
    (5, 6, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-001', 1, 1, NOW(6), NOW(6)),
    -- 솔라 — 100일의 태양
    (6, 7, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-012', 1, 1, NOW(6), NOW(6)),
    -- 파도 — 취미의 물결
    (7, 8, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-006', 1, 1, NOW(6), NOW(6)),
    -- 까루 — 알림 보고 왔어요
    (8, 9, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-007', 1, 1, NOW(6), NOW(6)),
    -- 동글 — 배움이 차곡차곡
    (9, 10, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-002', 1, 1, NOW(6), NOW(6)),
    -- 삐아 — 좋아요 요정
    (10, 11, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-010', 1, 1, NOW(6), NOW(6)),
    -- 호롱 — 딱 1분 남았어!
    (11, 12, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-011', 1, 1, NOW(6), NOW(6)),
    -- 다미 — 정리하면 다미
    (12, 13, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-008', 1, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `character_id`    = new_row.`character_id`,
                        `condition_key`   = new_row.`condition_key`,
                        `condition_param` = new_row.`condition_param`,
                        `target_count`    = new_row.`target_count`,
                        `sort_order`      = new_row.`sort_order`,
                        `updated_at`      = NOW(6);
