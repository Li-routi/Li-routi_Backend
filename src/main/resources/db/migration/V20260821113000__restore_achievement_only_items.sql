-- 업적 보상 전용 아이템 다섯을 원래 값으로 되돌린다.
--
-- R__seed_avatar_item 이 id 22~26 을 요술봉·카디건으로 덮어썼다. 그 id 는 시드가 아니라
-- V20260819235546 이 AUTO_INCREMENT 로 만든 자리인데, 시드가 "21 다음은 22" 로 보고 같은
-- 번호를 집었다. INSERT 가 중복 키에 걸려 UPDATE 로 넘어갔고, 이름·이미지·가격·정렬·활성이
-- 전부 판매 아이템 값으로 바뀌었다.
--
-- ⚠️ 슬롯까지 어긋났는데, 그건 시드가 slot 을 갱신 대상에서 빼 두었기 때문이다. 덮어쓰기가
--    나머지 컬럼만 바꾸는 바람에 "손 아이템 이름을 단 머리 아이템" 같은 행이 남았다.
--
-- 피해는 둘이다.
--   1. 비매품(price=0, active=FALSE)이 판매 상품(150~200, active=TRUE)이 됐다.
--      업적으로만 얻어야 할 한정 의상을 누구나 살 수 있는 상태였다.
--   2. achievement_reward_item.item_ref_id 가 이 행들을 가리켜, 업적을 받으면 "임금님 망토"
--      대신 "코랄 카디건" 이 지급된다. 보상 표기(item_name)와 실제 아이템이 어긋났다.
--
-- 구매·보유·착용은 0 건이었다. 그래서 slot 을 되돌려도 member_avatar_equipment 의 복합
-- 외래 키에 걸리지 않는다. 이 마이그레이션은 그 전제 위에 있다 — 누군가 착용한 뒤였다면
-- 슬롯을 되돌리지 못하고 새 아이템으로 옮겨야 했다.
--
-- 조인 키를 id 가 아니라 item_code 로 잡는다. item_code 는 시드가 건드리지 않은 컬럼이라
-- 덮어쓰기 뒤에도 정확하고, id 는 환경마다 다를 수 있다(AUTO_INCREMENT 로 만들어졌다).
--
-- 값의 출처는 V20260819235546 이다. 그 파일은 이미 적용됐으므로 수정하지 않고 여기서 되돌린다.

UPDATE `avatar_item`
SET `slot`       = 'HAND',
    `currency`   = 'TOPAZ',
    `price`      = 0,
    `name`       = '응원 폼폼',
    `image_key`  = 'avatar/item/hand/achievement-cheer-pompom-v1.png',
    `sort_order` = 100,
    `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` = 'ACH-SP-001-ITEM';

UPDATE `avatar_item`
SET `slot`       = 'HEAD',
    `currency`   = 'TOPAZ',
    `price`      = 0,
    `name`       = '100일 왕관',
    `image_key`  = 'avatar/item/head/achievement-100days-crown-v1.png',
    `sort_order` = 101,
    `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` = 'ACH-SP-002-ITEM';

UPDATE `avatar_item`
SET `slot`       = 'BODY',
    `currency`   = 'TOPAZ',
    `price`      = 0,
    `name`       = '파란 완주 유니폼',
    `image_key`  = 'avatar/item/body/achievement-months-uniform-v1.png',
    `sort_order` = 102,
    `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` = 'ACH-SP-003-ITEM';

UPDATE `avatar_item`
SET `slot`       = 'BODY',
    `currency`   = 'TOPAZ',
    `price`      = 0,
    `name`       = '토마토 셔츠',
    `image_key`  = 'avatar/item/body/achievement-housemate-shirt-1-v1.png',
    `sort_order` = 104,
    `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` = 'ACH-SP-004-ITEM';

UPDATE `avatar_item`
SET `slot`       = 'BODY',
    `currency`   = 'TOPAZ',
    `price`      = 0,
    `name`       = '임금님 망토',
    `image_key`  = 'avatar/item/body/achievement-cape-v1.png',
    `sort_order` = 106,
    `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` = 'ACH-SP-005-ITEM';

-- achievement_reward_item 은 건드리지 않는다. item_name 은 V20260820000243 이 만들 때
-- 복사해 둔 값이라 덮어쓰기 사고보다 앞서 있고, 지금도 원래 이름("응원 폼폼" …)이 그대로다.
-- 아이템 이름을 되돌리는 것으로 표기와 실물이 다시 맞는다.

-- 고아 행 정리.
--
-- ⚠️ 같은 아이템이 환경마다 다른 id 다. V20260819235546 이 AUTO_INCREMENT 로 넣었기 때문이다 —
--    개발 서버는 22~26, 로컬 테스트 DB 는 184~188 이었다. 그래서 잘못된 시드가 22~26 을 쓸 때
--    <b>환경에 따라 결과가 갈렸다.</b>
--
--      충돌한 환경(개발)  → UPDATE 로 넘어가 업적 아이템이 덮어써졌다. 위에서 되돌렸다.
--      충돌 안 한 환경     → 22~26 이 요술봉·카디건으로 새로 만들어졌다. 그 행이 여기 대상이다.
--
--    뒤쪽은 시드가 이제 그 id 를 쓰지 않으므로 <b>아무도 관리하지 않는 행</b>이 된다. 그대로 두면
--    상점에 같은 요술봉이 둘씩 뜬다. 지우지 않고 내리는 것은 이 표의 규칙 그대로다.
--
-- 위 UPDATE 들이 먼저 돌아야 한다. 개발 서버의 22~26 은 이 시점에 이미 업적 이미지로 돌아가
-- 있어서 아래 조건에 걸리지 않는다. 순서를 바꾸면 방금 되살린 것을 다시 내린다.
--
-- item_code IS NULL 이 "시드가 만든 것" 을 가른다. 업적 아이템에는 그 값이 있다.
UPDATE `avatar_item`
SET `active`     = FALSE,
    `updated_at` = NOW(6)
WHERE `item_code` IS NULL
  AND `image_key` IN (
                      'avatar/item/hand/wand-mint-v1.png',
                      'avatar/item/hand/wand-purple-v1.png',
                      'avatar/item/hand/wand-pink-v1.png',
                      'avatar/item/hand/wand-black-v1.png',
                      'avatar/item/body/cardigan-coral-v1.png'
    )
  AND `id` NOT IN (30, 31, 32, 33, 34);
