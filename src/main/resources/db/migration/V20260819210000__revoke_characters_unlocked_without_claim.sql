-- 업적을 받지 않고 열린 캐릭터를 회수한다.
--
-- 캐릭터마다 직접 해금 조건(ACTIVE_DAYS · CATEGORY_DAYS …)이 걸려 있던 동안, 업적의 "받기" 를
-- 누르지 않아도 조건 판정이 먼저 캐릭터를 열었다. 특히 민트는 ACTIVE_DAYS=1 이라 인증 한 번이면
-- 열려서, 회원 대부분이 업적을 받기도 전에 갖고 있었다.
--
-- 해금 경로를 업적 하나로 모으면서(R__seed_character_unlock_condition) 그렇게 얻은 보유를
-- 되돌린다. 그러지 않으면 "안 받았는데 갖고 있는" 행이 남아, 업적 목록의 받기 버튼과 상점의
-- 해금 상태가 계속 어긋난다.
--
-- ⚠️ 실제로 받은 사람의 것은 남긴다. member_achievement.status = 'CLAIMED' 인 조합만 지키고
--    나머지를 지운다 — 받은 사람까지 회수하면 이미 얻은 보상을 빼앗는 것이 된다.
--
-- ⚠️ 매핑을 이 파일 안에 적는다. character_unlock_condition 을 조인하고 싶지만 그 표는
--    R__ 시드가 채우고, 반복 마이그레이션은 V__ 가 전부 끝난 뒤에 돈다 — 이 시점에는 아직
--    옛 조건(ACTIVE_DAYS …)이 들어 있어 조인이 성립하지 않는다.
--
-- ⚠️ 기본 캐릭터 루티는 목록에 없다. 업적 보상이 아니라 가입 지급이라 회수 대상이 아니다.
--
-- 선택 중인 캐릭터를 회수하면 member_selected_character 의 복합 외래 키가 막으므로 선택을
-- 먼저 정리한다. 선택이 비면 조회 쪽이 보유 중 첫 번째로 대신 그린다.

CREATE TEMPORARY TABLE `tmp_revoke_target`
(
    `member_id`    BIGINT NOT NULL,
    `character_id` BIGINT NOT NULL,
    PRIMARY KEY (`member_id`, `character_id`)
) ENGINE = InnoDB;

INSERT INTO `tmp_revoke_target` (`member_id`, `character_id`)
SELECT mc.member_id, mc.character_id
FROM member_character mc
         JOIN `avatar_character` c ON c.id = mc.character_id
         JOIN (SELECT 'NOA' AS character_code, 'ACH-EG-003' AS achievement_code
               UNION ALL SELECT 'MORI', 'ACH-EG-009'
               UNION ALL SELECT 'KOKO', 'ACH-EG-004'
               UNION ALL SELECT 'YUKI', 'ACH-EG-005'
               UNION ALL SELECT 'MINT', 'ACH-EG-001'
               UNION ALL SELECT 'SOLA', 'ACH-EG-012'
               UNION ALL SELECT 'PADO', 'ACH-EG-006'
               UNION ALL SELECT 'KKARU', 'ACH-EG-007'
               UNION ALL SELECT 'DONGGEUL', 'ACH-EG-002'
               UNION ALL SELECT 'PPIA', 'ACH-EG-010'
               UNION ALL SELECT 'HORONG', 'ACH-EG-011'
               UNION ALL SELECT 'DAMI', 'ACH-EG-008') pair
              ON pair.character_code = c.code
         JOIN achievement a ON a.code = pair.achievement_code
         LEFT JOIN member_achievement ma
                   ON ma.member_id = mc.member_id AND ma.achievement_id = a.id
WHERE ma.id IS NULL
   OR ma.status <> 'CLAIMED';

DELETE msc
FROM member_selected_character msc
         JOIN `tmp_revoke_target` t
              ON t.member_id = msc.member_id AND t.character_id = msc.character_id;

DELETE mc
FROM member_character mc
         JOIN `tmp_revoke_target` t
              ON t.member_id = mc.member_id AND t.character_id = mc.character_id;

DROP TEMPORARY TABLE `tmp_revoke_target`;
