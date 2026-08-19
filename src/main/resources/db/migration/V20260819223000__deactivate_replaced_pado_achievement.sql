-- 대체된 파도 업적을 내린다.
--
-- 파도의 알을 주던 업적이 둘이 됐다. ACH-EG-006(취미의 물결, 취미 루틴 20일 누적) 이 원래
-- 것이고, V20260818142647 이 ACH-EG-013(파도의 도전, 선택한 루틴 10회 연속) 을 새로 넣으면서
-- 캐릭터 언락을 그쪽으로 옮겼다. 기획 문서의 "캐릭터 획득 조건 최종 정리" 표와 "세부조건
-- (연속형 규칙)" 이 서로 다른 조건을 적고 있었고, 연속형이 최신이라 그쪽을 따랐다.
--
-- 그런데 옛 업적을 내리지 않아 EGG 카테고리가 13개로 남았다. 캐릭터는 열둘이라 하나는
-- 알을 주지 못한다 — 목록에 알 그림 없는 EGG 업적이 뜨고, 받아도 아무것도 지급되지 않는다.
--
-- 지우지 않고 active = 0 으로 내린다. 지우면 member_achievement 의 외래 키가 걸리고, 나중에
-- "이 업적이 있었다" 를 되짚을 방법도 사라진다. 조회는 active = 1 만 읽으므로 화면에서는
-- 사라진다.
--
-- 진행도는 잃지 않는다. 적용 시점 실측으로 개발·운영 모두 이 업적의 member_achievement 가
-- 0건이고 파도를 가진 회원도 0명이다.

UPDATE achievement
SET active = FALSE
WHERE code = 'ACH-EG-006';

-- 보상 표의 옛 매핑도 함께 옮긴다.
--
-- achievement_reward_item 은 코드가 읽지 않는 표라 파도가 옮겨간 뒤에도 EG-006 을 가리킨 채
-- 남아 있었다. 사람이 캐릭터↔업적 매핑을 확인할 때 이 표를 보는데, 틀린 값이 남아 있으면
-- 그것을 근거로 시드를 잘못 쓰게 된다 — 실제로 그렇게 한 번 어긋났다.
UPDATE achievement_reward_item
SET achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-EG-013')
WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-EG-006')
  AND EXISTS (SELECT 1 FROM achievement WHERE code = 'ACH-EG-013');
