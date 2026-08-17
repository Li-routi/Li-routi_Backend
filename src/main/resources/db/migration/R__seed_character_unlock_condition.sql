-- 캐릭터 해금 조건 시드.
--
-- 파일 이름이 R__seed_character 뒤에 오도록 지었다. 반복 마이그레이션은 이름순으로 돌고,
-- 이 표는 avatar_character 를 외래 키로 참조하므로 마스터가 먼저 들어가야 한다.
--
-- id 는 시드가 직접 지정한다. 이 표에는 자연 키가 없어서(같은 캐릭터에 같은 키를 여러 번 걸
-- 수도 있다) id 로 걸지 않으면 매 배포마다 행이 쌓인다.
--
-- ⚠️ 루티(1)에는 조건이 없다. 행이 하나도 없으면 "항상 열려 있는 기본 캐릭터" 라는 뜻이고,
--    가입 시 기본 지급이 그 규칙으로 표현된다. 여기에 루티를 넣으면 안 된다.

-- 판정기 여덟. condition_param 의 뜻이 키마다 다르다.
--
--   ACTIVE_DAYS       활동한 날 수.                       param 없음
--   CATEGORY_DAYS     그 카테고리를 한 날 수.               param = 논리 키(쉼표면 합집합)
--   TIME_WINDOW_DAYS  그 시간대에 완료한 날 수.             param = "HH:MM-HH:MM" (KST)
--   ALL_KINDS_DAYS    하루에 개인·모임·챌린지를 모두 한 날 수. param 없음
--   ROUTINE_STREAK    한 개인 루틴을 예정일마다 연속 완료.    param 없음
--   STREAK_DAYS       활동일 연속.                        param 없음
--   LIKE_GIVEN        지금 좋아요를 눌러 둔 서로 다른 인증 수. param 없음
--   DEADLINE_RUSH     마감까지 남은 시간 안에 완료.          param = 초
--
-- 카테고리 논리 키는 세 체계에 이렇게 붙는다. id 를 직접 쓰지 않는 이유는 개인·그룹·챌린지가
-- 서로 다른 체계라서다.
--
--   EXERCISE 운동    개인·그룹 id 1   챌린지 EXERCISE
--   HEALTH   건강    개인·그룹 id 2   챌린지 HEALTH
--   SELF_DEV 자기계발 개인·그룹 id 3   챌린지 STUDY
--   LIFE     생활정리 개인·그룹 id 4   챌린지 LIFE
--   MIND     마음관리 개인·그룹 id 5   챌린지 MIND
--
-- ⚠️ 원안에서 둘을 바꿨다. 모리(자정에 앱 접속 중)와 까루(알림 눌러 접속)는 서버가 알 수 없다 —
--    클라이언트가 새 이벤트를 쏘고 그것을 받는 표가 있어야 하고, 조건 하나가 프론트 일정을
--    물고 들어온다. 성격을 살려 모리는 "한밤중에 완료", 까루는 "하루에 셋 다 완료" 로 옮겼다.

INSERT INTO `character_unlock_condition` (`id`, `character_id`, `condition_key`, `condition_param`,
                                          `target_count`, `sort_order`, `created_at`, `updated_at`)
VALUES
    -- 노아 — 오전 5:00~7:59 에 완료한 날 5일. 기획서 세부에 "기상·아침 성격 루틴" 이 있었으나
    -- 루틴의 성격은 서버가 알 수 없어 시간대로 읽었다.
    (1, 2, 'TIME_WINDOW_DAYS', '05:00-07:59', 5, 1, NOW(6), NOW(6)),
    -- 모리 — 한밤중(00:00~00:59) 완료 3일. 히든 성격을 살린 대체안이다.
    (2, 3, 'TIME_WINDOW_DAYS', '00:00-00:59', 3, 1, NOW(6), NOW(6)),
    -- 코코 — 운동 "또는" 건강. 행을 둘로 나누면 AND 가 되므로 한 행에 쉼표로 나열한다.
    (3, 4, 'CATEGORY_DAYS', 'EXERCISE,HEALTH', 20, 1, NOW(6), NOW(6)),
    (4, 5, 'CATEGORY_DAYS', 'MIND', 20, 1, NOW(6), NOW(6)),
    -- 민트 — 종류와 관계없이 처음 완료.
    (5, 6, 'ACTIVE_DAYS', NULL, 1, 1, NOW(6), NOW(6)),
    -- 솔라 — 연속이다. 하루라도 비면 0 으로 돌아간다. 활동일 기록이 지금 없어 표를 만든 날부터
    -- 세기 시작하므로, 첫 획득자는 그로부터 100일 뒤에 나온다.
    (6, 7, 'STREAK_DAYS', NULL, 100, 1, NOW(6), NOW(6)),
    -- 파도 — 개인 루틴 하나를 예정일마다 10회 연속. 고르는 화면이 없어 "하나라도" 로 읽었다.
    (7, 8, 'ROUTINE_STREAK', NULL, 10, 1, NOW(6), NOW(6)),
    -- 까루 — 하루에 개인·모임·챌린지를 모두 완료한 날 10일. 알림 클릭의 대체안이다.
    (8, 9, 'ALL_KINDS_DAYS', NULL, 10, 1, NOW(6), NOW(6)),
    (9, 10, 'CATEGORY_DAYS', 'SELF_DEV', 20, 1, NOW(6), NOW(6)),
    -- 삐아 — 지금 좋아요를 눌러 둔 서로 다른 인증 100 건. 취소가 하드 삭제라 누적은 셀 수 없어
    -- 뜻을 바꿨다. 100 건에 닿는 순간 해금되고, 그 뒤 취소해도 해금은 되돌리지 않는다.
    (10, 11, 'LIKE_GIVEN', NULL, 100, 1, NOW(6), NOW(6)),
    -- 호롱 — 마감까지 60 초 이하가 남았을 때 완료. 한 번이면 된다.
    (11, 12, 'DEADLINE_RUSH', '60', 1, 1, NOW(6), NOW(6)),
    (12, 13, 'CATEGORY_DAYS', 'LIFE', 20, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `character_id`    = new_row.`character_id`,
                        `condition_key`   = new_row.`condition_key`,
                        `condition_param` = new_row.`condition_param`,
                        `target_count`    = new_row.`target_count`,
                        `sort_order`      = new_row.`sort_order`,
                        `updated_at`      = NOW(6);
