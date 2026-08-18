-- 기존 회원에게 기본 캐릭터 보유·선택을 채운다.
--
-- 기본 캐릭터는 가입 흐름(MemberCommandService)이 해금 판정을 한 번 돌려서 준다. 그 경로가
-- 생기기 전에 가입한 회원에게는 행이 없고, 아바타 응답의 layers 가 통째로 비어 캐릭터도
-- 둥지도 그려지지 않는다. 인증을 하면 판정이 돌아 저절로 메워지지만, 그때까지 화면이
-- 깨져 보인다.
--
-- 캐릭터 표를 만든 마이그레이션과 따로 두는 이유는 그것이 이미 적용됐기 때문이다. 적용된
-- V__ 는 수정하지 않는다 — 체크섬이 어긋나면 부팅이 막힌다.

-- 1) 보유
--
-- 기본 캐릭터를 id 로 박지 않는다. "조건 행이 하나도 없으면 항상 열려 있는 기본 캐릭터"가
-- 규칙이므로 그 규칙 그대로 고른다. 나중에 조건 없는 캐릭터가 하나 더 생겨도 같은 뜻이 된다.
--
-- unlocked_date 를 KST 로 못박는다. CURRENT_DATE 는 DB 세션 시간대를 따르는데, 이 열의
-- 다른 값들은 애플리케이션이 KST 로 넣는다. 세션이 UTC 인 환경에서 돌면 하루가 밀린다.
--
-- 탈퇴 회원(is_active = 0)은 뺀다. 화면을 볼 사람이 없어 채워도 쓰이지 않고, 탈퇴 계정에
-- 새 데이터를 만드는 것은 탈퇴의 뜻과 어긋난다.
INSERT INTO member_character (member_id, character_id, unlocked_date, created_at, updated_at)
SELECT m.id,
       c.id,
       DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')),
       NOW(6),
       NOW(6)
FROM member m
         CROSS JOIN `avatar_character` c
WHERE m.is_active = 1
  AND c.active = 1
  AND NOT EXISTS (SELECT 1
                  FROM character_unlock_condition cc
                  WHERE cc.character_id = c.id)
  AND NOT EXISTS (SELECT 1
                  FROM member_character mc
                  WHERE mc.member_id = m.id
                    AND mc.character_id = c.id);

-- 2) 선택
--
-- 보유만 채우고 선택을 비워 두면 "회원에게 선택된 캐릭터가 항상 있다" 가 깨진다. 조회 쪽에
-- 보유 중 첫 번째로 대신하는 폴백이 있지만 그것은 방어책이지 정상 상태가 아니다.
--
-- 업적 보상으로 캐릭터를 먼저 받아 보유만 있고 선택이 빈 회원도 여기서 함께 메워진다.
--
-- 복합 FK 가 member_character 를 참조하므로 보유가 먼저다. 순서를 뒤집으면 실패한다.
--
-- 여기서도 탈퇴 회원을 뺀다. 위에서 보유를 안 넣었어도, 탈퇴 전에 캐릭터를 얻어 두고 선택만
-- 비어 있는 계정이 있을 수 있다.
INSERT INTO member_selected_character (member_id, character_id, created_at, updated_at)
SELECT mc.member_id,
       MIN(mc.character_id),
       NOW(6),
       NOW(6)
FROM member_character mc
         JOIN member m ON m.id = mc.member_id
WHERE m.is_active = 1
  AND NOT EXISTS (SELECT 1
                  FROM member_selected_character msc
                  WHERE msc.member_id = mc.member_id)
GROUP BY mc.member_id;
